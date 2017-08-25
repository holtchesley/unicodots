(ns unicodots.dot
  (:require [cljs.core.match :refer-macros [match]]
            [re-frame.loggers :as loggers]))


(def log (:log (loggers/get-loggers)))

(defn move-dot
  [{id ::id dot-dir ::dir :as dot} {:keys [reflect force] :as options}]
  (let [new-dir
        (match [(or force reflect) dot-dir]
               [::left  _]          ::left
               [::right _]          ::right
               [::up    _]          ::up
               [::down  _]          ::down
               [::positive ::left]  ::down
               [::positive ::right] ::up
               [::positive ::up]    ::right
               [::positive ::down]  ::left
               [::negative ::left]  ::up
               [::negative ::right] ::down
               [::negative ::up]    ::left
               [::negative ::down]  ::right
               [_ x]                x
               )]
    (-> dot
        (update ::location (fn [[x y]] (condp = new-dir
                                         ::left  [(dec x) y]
                                         ::right [(inc x) y]
                                         ::up    [x (dec y)]
                                         ::down  [x (inc y)])))
        (assoc ::dir new-dir))))

(defn reverse-direction
  [direction]
  (condp = direction
    ::left ::right
    ::right ::left
    ::up ::down
    ::down ::up))

(defn process-input
  [db]
  (let [inps (clojure.string/split (db :input) #"\n" 2)
        complete-input (pop inps)
        next-input (peek inps)
        dot-id (peek (db :input-queue))]
    (if (or (empty? complete-input)
            (nil? dot-id))
      db
      (-> db
          (update :input-queue pop)
          (assoc-in [:dots dot-id ::value ] complete-input)
          (assoc-in [:dots dot-id ::state] [::moving])))))


(def tile-fns
  {::fn-add +
   ::fn-minus -
   ::fn-times *
   ::fn-divide /
   ::fn-mod mod
   ::fn-exponent js/Math.pow
   ::fn-and (fn [x y] (and x y))
   ::fn-not (fn [x y] (not x))
   ::fn-or (fn [x y] (or x y))
   ::fn-xor (fn [x y] (and (not= x y) (or x y)))
   ::fn-greater-than >
   ::fn-greater-than-or-equal >=
   ::fn-equal =
   ::fn-not-equal not=
   ::fn-less-than-equal <=
   ::fn-less-than <
   })


(defn process-fn
  [db location]
  (let [tile (get-in db [:world location])
        f-d  (get-in tile [::location-type 1])
        f-r  (get-in tile [::location-type 2])
        hdot (peek (::horizontal-queue tile))
        vdot (peek (::vertical-queue tile))
        hval (get-in db [:dots hdot ::value])
        vval (get-in db [:dots vdot ::value])]
    (if (and hdot vdot)
      (-> db
        (update-in [:world location ::horizontal-queue] pop)
        (update-in [:world location ::vertical-queue] pop)
        (update :dots dissoc (if (= ::vertical f-d) hdot vdot))
        (update :ready-fns pop)
        (assoc-in [:dots (if (= ::vertical f-d) vdot hdot) ::state] [::moving])
        (assoc-in [:dots (if (= ::vertical f-d) vdot hdot) ::value]
                  (if (= ::vertical f-d) ((tile-fns f-r) vval hval) ((tile-fns f-r) hval vval)))
        (update-in [:dots (if (= ::vertical f-d) vdot hdot)] move-dot)
        )
      (update db :ready-fns pop)
      )))

(defn tile-defaults
  [tile-type]
  (assoc (if (contains? #{::conditional ::function} (first tile-type))
    {::horizontal-queue #queue []
     ::vertical-queue #queue []}
    {})
         ::location-type tile-type))


(defn transition-dot
  [dot tile]
  (let [lt (::location-type tile)
        ds (::state dot)
        actions
   (match
     [lt ds]
     ; If the dot is quoted, it's going to eat anything

     [[::escape] [::quote _]]       [[:set-dot-state dot (conj ds ::escape)] [:move-dot dot {}]]

     [[::quote] [::quote ::output]] [[:flush-output dot] [:set-dot-state dot [::moving]] [:move-dot dot {}]]
     [[::quote] [::quote _]]        [[:set-dot-state dot [::moving]] [:move-dot dot {}]]

     [[::value] [::quote ::output ::escape]] [[:accumulate-raw-output dot lt]         [:move-dot dot {}] [:set-dot-state dot (subvec ds 0 2)]]
     [[::value] [::quote ::output]]          [[:accumulate-output dot (dot ::value)]  [:move-dot dot {}]]

     [[::value] [::quote ::macro ::escape]]  [[:append-macro dot lt]          [:move-dot dot {}] [:set-dot-state dot (subvec ds 0 2)]]
     [[::value] [::quote ::macro]]           (conj (into [] (cond (string? (dot ::value)) (reverse (map (fn [x] [:append-macro dot [::literal x]]) (dot ::value)))
                                                                  (int? (dot ::value)) (reverse (map (fn [c] [:append-macro dot [::literal (int c)]]) (str (dot ::value))))))
                                                   [:move-dot dot {}])

     [[::value] [::quote ::pipe ::escape]]   [[:send-raw-input lt]          [:move-dot dot {}] [:set-dot-state dot (subvec ds 0 2)]]
     [[::value] [::quote ::pipe]]            [[:send-input  (dot ::value)]  [:move-dot dot {}]]

     [_ [::quote ::output & e]] [[:accumulate-raw-output lt]   [:move-dot dot {}] (when (first e) [:set-dot-state dot (subvec ds 0 2)])]
     [_ [::quote ::value  & e]] [[:set-raw-value dot lt]       [:move-dot dot {}] (when (first e) [:set-dot-state dot (subvec ds 0 2)])]
     [_ [::quote ::macro  & e]] [[:append-macro dot lt ]       [:move-dot dot {}] (when (first e) [:set-dot-state dot (subvec ds 0 2)])]
     [_ [::quote ::pipe   & e]] [[:send-raw-input lt   ]       [:move-dot dot {}] (when (first e) [:set-dot-state dot (subvec ds 0 2)])]


     ; Similar for enqueue, but no movement
     [_ [::enqueued & t]] []

     ; Death
     [[::die] _] [[:kill-dot dot]]

     ; Basic movement, reflection, merging, pathing
     [[::reflect direction] _] [[:move-dot dot {:reflect direction}]]
     [[::merge   direction] _] [[:move-dot dot {:force direction}]]
     [[::path    direction] _] (match [direction (dot ::dir)]
                                          [::both _] [[:set-dot-state dot [::moving]][:move-dot dot {}]]
                                          [::horizontal (:or ::left ::right)] [[:set-dot-state dot [::moving]][:move-dot dot {}]]
                                          [::vertical (:or ::up ::down)] [[:set-dot-state dot [::moving]][:move-dot dot {}]]
                                          :else [[:kill-dot dot]])

     ; Moving state setters Val, Macro, Output,Pipe,Quote
     [[::value] [::output]]  [[:send-output (dot ::value)] [:move-dot dot {}]     [:set-dot-state dot [::moving]]]
     [[::value] [::pipe]]    [[:send-input (dot ::value)]   [:move-dot dot {}]    [:set-dot-state dot [::moving]]]
     [[::value] [::macro]]   [[:set-macro dot (dot ::value)]   [:move-dot dot {}] [:set-dot-state dot [::moving]]]
     [[::value] [::moving]]  [[:set-dot-state dot [::value]] [:move-dot dot {}]]

     [[::macro] [::output]]  [[:send-output (dot ::macro)] [:move-dot dot {}]]
     [[::macro] [::pipe]]    [[:send-input (dot ::macro)]   [:move-dot dot {}]]
     [[::macro] [::value]]   [[:set-value dot (dot ::macro)]   [:move-dot dot {}]]
     [[::macro] [::moving]]  [[:set-dot-state dot [::macro]] [:move-dot dot {}]]

     [[::output] [::output]]  [[:move-dot dot {}]]
     [[::output] [::pipe]]    [[:move-dot dot {}]]
     [[::output] [::value]]   [[:move-dot dot {}]]
     [[::output] [::moving]]  [[:set-dot-state dot [::output]] [:move-dot dot {}]]

     [[::pipe] [::output]]  [[:move-dot dot {}]]
     [[::pipe] [::pipe]]    [[:move-dot dot {}]]
     [[::pipe] [::value]]   [[:move-dot dot {}]]
     [[::pipe] [::moving]]  [[:set-dot-state dot [::pipe]] [:move-dot dot {}]]

     [[::quote] [::output]]      [[:set-dot-state dot [::quote ::output]] [:move-dot dot {}]]
     [[::quote] [::pipe]]        [[:set-dot-state dot [::quote ::pipe]] [:move-dot dot {}]]
     [[::quote] [::value]]       [[:set-dot-state dot [::quote ::value]] [:move-dot dot {}]]
     [[::quote] [::quote & _]]   [[:end-dot-quote dot] [:move-dot dot {}]]
     [[::quote] [::moving]]      [[:set-dot-state dot [::quote]] [:move-dot dot {}]]
     [[::quote] [::macro]]       [[:set-dot-state dot [::quote ::macro]] [:move-dot dot {}]]


     [[::literal x] [::value]] [[:append-value dot x] [:move-dot dot {}]]
     [[::literal _] [::moving]] [[:move-dot dot {}]]
     [[::literal x] [::output]] [[:send-output x][:move-dot dot {}]]

     ; Duplication
     [[::duplicate] _] [[:duplicate-dot dot tile]]


     ; Enqueued Dots
     [_ [::enqueued]] []

     ; Functions, Conditionals, Input (Require Queuing)
     [[(:or ::conditional ::fn) & _] _] [[:enqueue-fn tile dot]]
     [[::input] _]     [[:enqueue-input dot]]

     [[::empty-tile] _] (if (empty? (dot ::macro-stack))
                          [[:kill-dot dot]]
                          [[:apply-macro dot]])
     [_ _] nil
     )]
    ;; (when (nil? actions)
      (println "actions" lt ds " - " actions)
      ;; )
    (vec (filter identity actions))
    )
  )







(comment
  dot-state
  [[::quote ::macro]
   [::quote ::value]
   [::quote ::output]
   [::quote ::pipe]
   [::quote]
   [::enqueued]
   [::pathing]
   [::output]
   [::macro]
   [::val]
   [::pipe]])

(comment
  {::value 1
   ::x 0
   ::y 0
   ::dir [::left ::right ::up ::down]
   ::state ::moving
   ::state-data {}
   })


(comment
  tile-states
  [::path [::horizontal ::vertical ::both]
   ::merge [::left ::right ::up ::down]
   ::reflect [::left ::right ::up ::down ::positive ::negative]
   ::value
   ::input
   ::output
   ::macro
   ::pipe
   ::literal
   ::quote
   ::duplicate
   ::fn [::vertical ::horizontal + ::fn-type]
   ])




(comment
  location-states
  {::location [x y]
   ::location-type ::path-vertical
   ::warp-locations [[1 2] [2 4]]
   ::duplicate [::left ::right ::up]
   ::horizontal-queue #queue []
   ::vertical-queue #queue []})

(comment
  symbols
  {\| ::path-vertical
   \- ::path-horizontal
   \& ::die
   \+ ::path-same
   \> ::merge-right
   \^ ::merge-up
   \v ::merge-down
   \< ::merge-left
   \/ ::reflect-positive
   \\ ::reflect-negative
   \( ::reflect-right
   \) ::reflect-left
   \U ::reflect-up
   \Ω ::reflect-down
   \~ ::conditional
   \* ::duplicate
   \# ::set-val
   \[ ::start-vertical-fn
   \] ::end-vertical-fn
   \{ ::start-horizontal-fn
   \} ::end-horizontal-fn
   \X ::warp
   \" ::quote
   \1 ::literal
   \@ ::macro})

(comment
  fns
  {\+ ::fn-add
   \- ::fn-minus
   \* ::fn-times
   \/ ::fn-divide
   \% ::fn-mod
   \^ ::fn-exponent
   \& ::fn-and
   \! ::fn-not
   \o ::fn-or
   \x ::fn-xor
   \> ::fn-greater-than
   \≥ ::fn-greater-than-or-equal
   \= ::fn-equal
   \≠ ::fn-not-equal
   \≤ ::fn-less-than-equal
   \< ::fn-less-than})


