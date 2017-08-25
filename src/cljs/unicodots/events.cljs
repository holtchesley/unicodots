(ns unicodots.events
  (:require [re-frame.core :as re-frame]
            [re-frame.loggers :as loggers]
            [unicodots.db :as db]
            [unicodots.dot :as dot]
            [cljs.core.match :refer-macros [match]]
            ))

(def log (:log (loggers/get-loggers)))

(re-frame/reg-event-db
 :initialize-db
 (fn  [_ _]
   db/default-db))

(re-frame/reg-event-db
 :update-input
 (fn [db [_ new-text]]
   (assoc db :input new-text)))

(re-frame/reg-event-db
 :move-dot
 (fn [db [_ {id ::dot/id} options]]
   (update-in db [:dots id] dot/move-dot options)
   ))

(re-frame/reg-event-db
 :send-output
 (fn [db [_ val]]
   (update db :output conj val)))


(re-frame/reg-event-db
 :send-input
 (fn [db [_ val]]
   (update db :input str "\n" val)))

(re-frame/reg-event-db
 :set-macro
 (fn [db [_ dot val]]
   (assoc-in db [:dots (get dot ::dot/id) ::dot/macro] val)))

(re-frame/reg-event-db
 :set-dot-state
 (fn [db [_ dot new-state]]
   (if (= new-state [::dot/value])
     (-> db
         (assoc-in [:dots (get dot ::dot/id) ::dot/value] 0)
         (assoc-in [:dots (get dot ::dot/id) ::dot/state] new-state))
     (assoc-in db [:dots (get dot ::dot/id) ::dot/state] new-state))))

(re-frame/reg-event-db
 :set-value
 (fn [db [_ dot val]]
   (assoc-in db [:dots (get dot ::dot/id) ::dot/value] val)))

(re-frame/reg-event-db
 :duplicate-dot
 (fn [db [_ dot tile]]
   (let [id (::dot/id dot)
         dir (::dot/dir dot)
         start-id (:next-dot-id db)
         [x y] (::dot/location dot)
         lds (filter (fn [x] (and x (first x)))
                     (map (fn [[l d]]
                            (and (not= (dot/reverse-direction dir) d)
                                 [(get-in db [:world l ::dot/location]) d]))
                          [[[(dec x) y] ::dot/left]
                           [[(inc x) y] ::dot/right]
                           [[x (dec y)] ::dot/up]
                           [[x (inc y)] ::dot/down]]))
         spawns (into {}
                      (map-indexed (fn [id-offset [location direction]]
                                     {(+ id id-offset)
                                      (merge dot
                                             {::dot/id (+ id id-offset)
                                              ::dot/location location
                                              ::dot/dir direction})})
                                   lds))]
     (-> db
         (update :next-dot-id + (count spawns))
         (update :dots merge spawns)
         ))))


(re-frame/reg-event-db
 :enqueue-fn
 (fn [db [_ tile dot]]
   (let [tile-location (::dot/location tile)
         dir (get dot ::dot/dir)
         qk ({::dot/left  ::dot/horizontal-queue
              ::dot/right ::dot/horizontal-queue
              ::dot/up    ::dot/vertical-queue
              ::dot/down  ::dot/vertical-queue
              } dir)
         ndb (-> db
                 (update-in [:world tile-location qk] conj (::dot/id dot))
                 (assoc-in [:dots (::dot/id dot) ::dot/state] [::dot/enqueued]))]
     (if (and (get-in ndb [:world tile-location ::dot/horizontal-queue])
              (get-in ndb [:world tile-location ::dot/vertical-queue]))
       (update ndb :ready-fns conj tile-location)
       ndb))))


(re-frame/reg-event-db
 :enqueue-input
 (fn [db [_ dot]]
   (let [id (::dot/id dot)]
     (-> db
         (update :input-queue conj id)
         (assoc-in [:dots id ::dot/state] [::dot/enqueued (first (::dot/state dot))])))))


(re-frame/reg-event-db
 :append-value
 (fn [db [_ dot val]]
   (let [old-val (::dot/value dot)
         new-val
         (cond
           (number? old-val) (+ val (* 10 old-val))
           (string? old-val) (str old-val val)
           :else val)]
     (assoc-in db [:dots (::dot/id dot) ::dot/value] new-val))))

(re-frame/reg-event-fx
 :accumulate-raw-output
 (fn [{db :db} [_ dot tile-type]]
   (let [s (get (:theme db) tile-type (second tile-type))]
     {:dispatch [:accumulate-output dot s]})))

(re-frame/reg-event-db
 :accumulate-output
 (fn [db [_ {id ::dot/id :as dot} string]]
   (update-in db [:dots id ::dot/output] str string)))

(re-frame/reg-event-db
 :flush-output
 (fn [db [_ {id ::dot/id}]]
   (let [output (get-in db [:dots id ::dot/output])]
     (-> db
         (update :output conj output)
         (update-in [:dots id] dissoc ::dot/output)))))

(re-frame/reg-event-fx
 :send-raw-output
 (fn [{db :db} [_ tile-type]]
   (let [s (get (:theme db) tile-type (second tile-type))]
     {:dispatch [:send-output s]}
     )))

(re-frame/reg-event-fx
 :set-raw-value
 (fn [{db :db} [_ dot tile-type]]
   (let [s (get (:theme db) tile-type (second tile-type))]
     {:dispatch [:set-value dot s]})))

(re-frame/reg-event-db
 :append-macro
 (fn [db [_ {id ::dot/id} lt]]
   (update-in db [:dots id ::dot/macro-stack] conj lt)
   ))

(re-frame/reg-event-fx
 :send-raw-input
 (fn [{db :db} [_ lt escaped]]
   (let [s (get (:theme db) lt (second lt))]
     {:dispatch [:send-input s]})))


(re-frame/reg-event-db
 :end-dot-quote
 (fn [db [_ dot]]
   db))

(re-frame/reg-event-fx
 :step-program
 (fn [{db :db} _]
   (let [dot-tiles (map
                    (fn [dot]
                      (let [tile (or (get-in db [:world (::dot/location dot)]) {::dot/location-type [::dot/empty-tile]})]
                        [dot  tile]))
                     (-> db :dots vals))
         dot-steps (into [] (mapcat
                             (fn [[d t]] (dot/transition-dot d t))
                             dot-tiles))]
     {:dispatch-n (-> dot-steps (conj [:process-fns]) (conj [:process-inputs]))}
     )))


(re-frame/reg-event-db
 :process-fns
 (fn [db _]
   (loop
    [d db]
    (let [loc (-> d :ready-fns peek)]
      (if (nil? loc)
        d
        (recur (dot/process-fn d loc)))))))


(re-frame/reg-event-db
 :process-inputs
 (fn [db _]
   (dot/process-input db)))


(defn step-dispatcher
  []
  (re-frame/dispatch [:step-program]))

(re-frame/reg-event-db
 :set-step-timer
 (fn [db [_ val]]
   (if-let [timer (get db :step-timer)]
     (do (js/clearTimeout timer)
         (-> db
             (assoc :step-delay val)
             (assoc :step-timer (js/setInterval step-dispatcher val))))
     (assoc db :step-delay val))))

(re-frame/reg-event-db
 :start-program-stepping
 (fn [db _]
   (let [timing (db :step-delay)
         curr-timer (db :step-timer)]
     (when curr-timer
       (js/clearTimeout curr-timer))
     (let [timer (js/setInterval step-dispatcher timing)]
       (assoc db :step-timer timer)))))

(re-frame/reg-event-db
 :stop-program-stepping
 (fn [db _]
   (if-let [timer (get db :step-timer)]
     (do (js/clearTimeout timer) (dissoc db :step-timer))
     db)))

(re-frame/reg-event-db
 :offset-world
 (fn [db [_ x y]]
   (let [world (reduce-kv
                (fn [m [lx ly] tile]
                  (let [nloc [(+ lx x) (+ ly y)]]
                    (merge m
                           {nloc (assoc tile ::dot/location nloc)})))
                {}
                (get db :world))
         dots (reduce-kv
               (fn [m i v]
                 (assoc m i (update v ::dot/location (fn [[dx dy]] [(+ dx x) (+ dy y)])))
                 )
               {}
               (get db :dots))
         ]
     (-> db
         (assoc :world world)
         (assoc :dots dots)))))


                                        ; There are 3 cases
                                        ; 1 - tile-type is empty : do nothing
                                        ; 2 - tile-type is a pre-function : do nothing
                                        ; 3 - Other : spawn the tile at that point

(re-frame/reg-event-db
 :set-tile
 (fn [db [_ loc tile-type]]
   (cond
     (nil? tile-type) db
     (= ::dot/pre-function (first tile-type)) db
     (= [::dot/empty-tile] tile-type) (update db :world dissoc loc)
     :else (assoc-in db [:world loc] (assoc (dot/tile-defaults tile-type) ::dot/location loc)))))

(re-frame/reg-event-fx
 :paint-tile
 (fn [{db :db} [_ loc]]
   {:dispatch-n (if (= ::dot/dot (get-in db [:current-paint 0]))
                  [[:add-dot loc]]
                  [[:set-tile loc (get db :current-paint)]
                   [:pad-top-left]])}))

(def fn-keypress-map
  {[::dot/path ::dot/both]                        ::dot/fn-add
   [::dot/path ::dot/horizontal]                  ::dot/fn-minus
   [::dot/fn-stub ::dot/fn-divide]                ::dot/fn-divide
   [::dot/duplicate]                              ::dot/fn-multiply
   [::dot/fn-stub ::dot/fn-modulus]               ::dot/fn-modulus
   [::dot/merge ::dot/up]                         ::dot/fn-exponent
   [::dot/literal \a]                             ::dot/fn-and
   [::dot/literal \o]                             ::dot/fn-or
   [::dot/literal \x]                             ::dot/fn-xor
   [::dot/merge ::dot/right]                      ::dot/fn-greater-than
   [::dot/fn-stub ::dot/fn-greater-than-or-equal] ::dot/fn-greater-than-or-equal
   [::dot/fn-stub ::dot/fn-equal]                 ::dot/fn-equal
   [::dot/fn-stub ::dot/fn-not-equal]             ::dot/fn-not-equal
   [::dot/fn-stub ::dot/fn-less-than-or-equal]    ::dot/fn-less-than-or-equal
   [::dot/merge ::dot/left]                       ::dot/fn-less-than}
  )

(re-frame/reg-event-db
 :add-dot
 (fn [db [_ loc]]
   (let [id (get db :next-dot-id)
         glyph (-> db :theme :dot-seeds peek)
         dir (get-in db [:current-paint 1])
         dot #::dot{:id id :glyph glyph :value 0 :location loc :dir dir :state [::dot/moving] :macro-stack []}
         ]
     (-> db
         (assoc-in [:dots id] dot)
         (update-in [:theme :dot-seeds] (fn [x] (into [(peek x)] (pop x))))
         (update :next-dot-id inc))
     )))

(re-frame/reg-event-db
 :set-current-paint
 (fn [db [_ tile-type]]
   (let [current-paint (get db :current-paint)
         fn-deref (get fn-keypress-map tile-type)
         new-tile (if (and fn-deref (= (first current-paint) ::dot/pre-function))
                    [::dot/fn (second current-paint) fn-deref] tile-type)]
   (assoc db :current-paint new-tile))))

(re-frame/reg-event-db
 :toggle-current-paint
 (fn [db [_ tile-type]]
   (if (= (get db :current-paint) tile-type)
     (dissoc db :current-paint)
     (assoc db :current-paint tile-type))))

(re-frame/reg-event-fx
 :pad-top-left
 (fn [{db :db} _]
   (let [[left top] (->> db :world keys
                         (reduce
                          (fn [ms ns]
                            [(min (first ms) (first ns)) (min (second ms) (second ns))])
                          [100 100]))
         left-buf (max 0 (- 4 left))
         top-buf (max 0 (- 4 top))]
     (if (= 0 left-buf top-buf)
       {}
       {:dispatch [:offset-world left-buf top-buf]}))))

(re-frame/reg-event-db
 :pop-macro
 (fn [db [_ {id ::dot/id}]]
   (update-in db [:dots id ::dot/macro-stack] pop)))

(re-frame/reg-event-fx
 :apply-macro
 (fn [{db :db} [_ {id ::dot/id loc ::dot/location mac ::dot/macro-stack :as dot}]]
   (let [tile (peek mac)]
     {:dispatch-n (if (= ::dot/dot tile)
                    [[:add-dot loc] [:pop-macro dot]]
                    [[:set-tile loc tile]
                     [:pop-macro dot]
                     [:pad-top-left]])})))

(re-frame/reg-event-db
 :kill-dot
 (fn [db [_ {id ::dot/id}]]
   (update db :dots dissoc id)))
