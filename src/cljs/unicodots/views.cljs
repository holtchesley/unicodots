(ns unicodots.views
  (:require [re-frame.core :as re-frame]
            [re-frame.loggers :as loggers]
            [unicodots.dot :as dot]
            [cljs.core.match :refer-macros [match]]
            [goog.events :as events]
            [clojure.string :refer [join]])
  (:import [goog.events EventType]))

(def log (:log (loggers/get-loggers)))

(defn grid-coord
  [[x y]]
  {:style {:grid-column x :grid-row y}})

(def location-type-map
  {[::dot/empty-tile] "■"
   [::dot/reflect ::dot/positive] "/"
   [::dot/reflect ::dot/negative] "\\"
   [::dot/reflect ::dot/left] ")"
   [::dot/reflect ::dot/right] "("
   [::dot/reflect ::dot/up] "U"
   [::dot/reflect ::dot/down] "Ω"

   [::dot/merge ::dot/left] "<"
   [::dot/merge ::dot/right] ">"
   [::dot/merge ::dot/up] "^"
   [::dot/merge ::dot/down] "V"

   [::dot/path ::dot/horizontal] "—"
   [::dot/path ::dot/vertical] "|"
   [::dot/path ::dot/both] "+"


   [::dot/die] "&"

   [::dot/duplicate] "*"
   [::dot/value] "#"
   [::dot/quote] "\""
   [::dot/output] "$"
   [::dot/input] "?"
   [::dot/macro] "@"

   [::dot/conditional ::dot/vertical] "︴"
   [::dot/conditional ::dot/horizontal] "~"


   [::dot/fn ::dot/vertical ::dot/fn-add]                     "[+]"
   [::dot/fn ::dot/vertical ::dot/fn-minus]                   "[-]"
   [::dot/fn ::dot/vertical ::dot/fn-divide]                  "[÷]"
   [::dot/fn ::dot/vertical ::dot/fn-multiply]                "[*]"
   [::dot/fn ::dot/vertical ::dot/fn-mod]                     "[%]"
   [::dot/fn ::dot/vertical ::dot/fn-exponent]                "[^]"
   [::dot/fn ::dot/vertical ::dot/fn-and]                     "[a]"
   [::dot/fn ::dot/vertical ::dot/fn-or]                      "[o]"
   [::dot/fn ::dot/vertical ::dot/fn-xor]                     "[x]"
   [::dot/fn ::dot/vertical ::dot/fn-greater-than]            "[>]"
   [::dot/fn ::dot/vertical ::dot/fn-greater-than-or-equal]   "[≥]"
   [::dot/fn ::dot/vertical ::dot/fn-equal]                   "[=]"
   [::dot/fn ::dot/vertical ::dot/fn-not-equal]               "[≠]"
   [::dot/fn ::dot/vertical ::dot/fn-less-than-or-equal]      "[≤]"
   [::dot/fn ::dot/vertical ::dot/fn-less-than]               "[<]"

   [::dot/fn ::dot/horizontal ::dot/fn-add]                   "{+}"
   [::dot/fn ::dot/horizontal ::dot/fn-minus]                 "{-}"
   [::dot/fn ::dot/horizontal ::dot/fn-divide]                "{÷}"
   [::dot/fn ::dot/horizontal ::dot/fn-multiply]              "{*}"
   [::dot/fn ::dot/horizontal ::dot/fn-mod]                   "{%}"
   [::dot/fn ::dot/horizontal ::dot/fn-exponent]              "{^}"
   [::dot/fn ::dot/horizontal ::dot/fn-and]                   "{a}"
   [::dot/fn ::dot/horizontal ::dot/fn-or]                    "{o}"
   [::dot/fn ::dot/horizontal ::dot/fn-xor]                   "{x}"
   [::dot/fn ::dot/horizontal ::dot/fn-greater-than]          "{>}"
   [::dot/fn ::dot/horizontal ::dot/fn-greater-than-or-equal] "{≥}"
   [::dot/fn ::dot/horizontal ::dot/fn-equal]                 "{=}"
   [::dot/fn ::dot/horizontal ::dot/fn-not-equal]             "{≠}"
   [::dot/fn ::dot/horizontal ::dot/fn-less-than-or-equal]    "{≤}"
   [::dot/fn ::dot/horizontal ::dot/fn-less-than]             "{<}"
   })

(defn get-location-image
  [tile-type]
  (get location-type-map tile-type "�")
  )

(defn empty-location
  [l t]
  ^{:key [:location l]}
  [:div (merge {:on-click #(re-frame/dispatch [:paint-tile l])}
               (grid-coord l)) t])

(defn tiled-location
  [{l ::dot/location t ::dot/location-type} theme]
  ^{:key [:location l]}
  [:div (merge {:on-click #(re-frame/dispatch [:paint-tile l])} (grid-coord l))
   (if (= (first t) ::dot/literal)
     (second t)
     (get theme t "?"))])

(defn top-bar
  []
  (let [delay-slider-val (re-frame/subscribe [:step-delay-val])]
    (fn []
      [:div#header {:class "top-bar"}
       [:button {:on-click #(re-frame/dispatch [:step-program])} "⏩"]
       [:button {:on-click #(re-frame/dispatch [:start-program-stepping])} "▶"]
       [:button {:on-click #(re-frame/dispatch [:stop-program-stepping])} "⏸"]
       [:div "50ms"]
       [:input {:type "range" :min 0 :max 2 :step "0.01" :value @delay-slider-val
                :on-change #(re-frame/dispatch [:set-step-timer (int (* 50 (.pow js/Math 10 (.. % -target -value))))])}]
       [:div "5000ms"]
       ])))

(defn output-display
  []
  (let [outputs (re-frame/subscribe [:output])]
    (fn []
      [:div {:class "outputs"}
       [:div "__Output__"]
       [:div {:class "p-container"}
        (map-indexed
         (fn [i o] ^{:key i} [:p o])
         @outputs)]])))

(defn input-display
  []
  (let [input (re-frame/subscribe [:input])]
    (fn []
      [:div {:class "inputs"}
       [:div "__Inputs__"]
       [:textarea {:value @input
                   :on-change (fn [e]
                                (re-frame/dispatch-sync [:update-input (.. e -target -value)]))}]]
      )
    ))

(defn right-bar
  []
  (let []
    (fn []
      [:div {:class "right-bar"}
       [:div {:class "diagnostics"}]
       [input-display]
       [output-display]])))

(defn left-bar
  []
  (let [theme (re-frame/subscribe [:theme])
        tile-paint (re-frame/subscribe [:current-paint])]
    (fn []
      [:div {:class "left-bar"}
       [:div.palettes
        [:p "Paths"]
        [:div
         [:button {:class (when (= @tile-paint [::dot/path ::dot/horizontal]) "selected")
                   :on-click #(re-frame/dispatch [:toggle-current-paint [::dot/path ::dot/horizontal]])}
          (get @theme [::dot/path ::dot/horizontal])]
         [:button {:class (when (= @tile-paint [::dot/path ::dot/vertical]) "selected")
                   :on-click #(re-frame/dispatch [:toggle-current-paint [::dot/path ::dot/vertical]])}
          (get @theme [::dot/path ::dot/vertical])]
         [:button {:class (when (= @tile-paint [::dot/path ::dot/both]) "selected")
                   :on-click #(re-frame/dispatch [:toggle-current-paint [::dot/path ::dot/both]])}
          (get @theme [::dot/path ::dot/both])]
         [:button {:class (when (= @tile-paint [::dot/reflect ::dot/positive]) "selected")
                   :on-click #(re-frame/dispatch [:toggle-current-paint [::dot/reflect ::dot/positive]])}
          (get @theme [::dot/reflect ::dot/positive])]
         [:button {:class (when (= @tile-paint [::dot/reflect ::dot/negative]) "selected")
                   :on-click #(re-frame/dispatch [:toggle-current-paint [::dot/reflect ::dot/negative]])}
          (get @theme [::dot/reflect ::dot/negative])]
         [:button {:class (when (= @tile-paint [::dot/reflect ::dot/left]) "selected")
                   :on-click #(re-frame/dispatch [:toggle-current-paint [::dot/reflect ::dot/left]])}
          (get @theme [::dot/reflect ::dot/left])]
         [:button {:class (when (= @tile-paint [::dot/reflect ::dot/right]) "selected")
                   :on-click #(re-frame/dispatch [:toggle-current-paint [::dot/reflect ::dot/right]])}
          (get @theme [::dot/reflect ::dot/right])]
         [:button {:class (when (= @tile-paint [::dot/reflect ::dot/up]) "selected")
                   :on-click #(re-frame/dispatch [:toggle-current-paint [::dot/reflect ::dot/up]])}
          (get @theme [::dot/reflect ::dot/up])]
         [:button {:class (when (= @tile-paint [::dot/reflect ::dot/down]) "selected")
                   :on-click #(re-frame/dispatch [:toggle-current-paint [::dot/reflect ::dot/down]])}
          (get @theme [::dot/reflect ::dot/down])]
         [:button {:class (when (= @tile-paint [::dot/merge ::dot/left]) "selected")
                   :on-click #(re-frame/dispatch [:toggle-current-paint [::dot/merge ::dot/left]])}
          (get @theme [::dot/merge ::dot/left])]
         [:button {:class (when (= @tile-paint [::dot/merge ::dot/right]) "selected")
                   :on-click #(re-frame/dispatch [:toggle-current-paint [::dot/merge ::dot/right]])}
          (get @theme [::dot/merge ::dot/right])]
         [:button {:class (when (= @tile-paint [::dot/merge ::dot/up]) "selected")
                   :on-click #(re-frame/dispatch [:toggle-current-paint [::dot/merge ::dot/up]])}
          (get @theme [::dot/merge ::dot/up])]
         [:button {:class (when (= @tile-paint [::dot/merge ::dot/down]) "selected")
                   :on-click #(re-frame/dispatch [:toggle-current-paint [::dot/merge ::dot/down]])}
          (get @theme [::dot/merge ::dot/down])]
         [:button {:class (when (= @tile-paint [::dot/die]) "selected")
                   :on-click #(re-frame/dispatch [:toggle-current-paint [::dot/die]])}
          (get @theme [::dot/die])]]
        [:p "Misc"]
        [:div
         [:button {:class (when (= @tile-paint  [::dot/duplicate]) "selected")
                   :on-click #(re-frame/dispatch [:toggle-current-paint [::dot/duplicate]])}
          (get @theme  [::dot/duplicate])]
         [:button {:class (when (= @tile-paint  [::dot/value]) "selected")
                   :on-click #(re-frame/dispatch [:toggle-current-paint [::dot/value]])}
          (get @theme  [::dot/value])]
         [:button {:class (when (= @tile-paint  [::dot/quote]) "selected")
                   :on-click #(re-frame/dispatch [:toggle-current-paint [::dot/quote]])}
          (get @theme  [::dot/quote])]
         [:button {:class (when (= @tile-paint  [::dot/output]) "selected")
                   :on-click #(re-frame/dispatch [:toggle-current-paint [::dot/output]])}
          (get @theme  [::dot/output])]
         [:button {:class (when (= @tile-paint  [::dot/input]) "selected")
                   :on-click #(re-frame/dispatch [:toggle-current-paint [::dot/input]])}
          (get @theme  [::dot/input])]
         [:button {:class (when (= @tile-paint  [::dot/macro]) "selected")
                   :on-click #(re-frame/dispatch [:toggle-current-paint [::dot/macro]])}
          (get @theme  [::dot/macro])]
         ]
        [:p "Conditionals"]
        [:div
         [:button {:class (when (= @tile-paint [::dot/conditional ::dot/vertical]) "selected")
                   :on-click #(re-frame/dispatch [:toggle-current-paint [::dot/conditional ::dot/vertical]])}
          (get @theme [::dot/conditional ::dot/vertical])]
         [:button {:class (when (= @tile-paint [::dot/conditional ::dot/horizontal]) "selected")
                   :on-click #(re-frame/dispatch [:toggle-current-paint [::dot/conditional ::dot/horizontal]])}
          (get @theme [::dot/conditional ::dot/horizontal])]]

        [:p "Vertical Functions"]
        [:div {:class (when (= @tile-paint [::dot/pre-function ::dot/vertical]) "selected")}
         [:button {:class (when (= @tile-paint [::dot/fn ::dot/vertical ::dot/fn-add]                    ) "selected")
                   :on-click #(re-frame/dispatch [:toggle-current-paint [::dot/fn ::dot/vertical ::dot/fn-add]])}
          (get @theme [::dot/fn ::dot/vertical ::dot/fn-add]                    )]
         [:button {:class (when (= @tile-paint [::dot/fn ::dot/vertical ::dot/fn-minus]                  ) "selected")
                   :on-click #(re-frame/dispatch [:toggle-current-paint [::dot/fn ::dot/vertical ::dot/fn-minus]])}
          (get @theme [::dot/fn ::dot/vertical ::dot/fn-minus]                  )]
         [:button {:class (when (= @tile-paint [::dot/fn ::dot/vertical ::dot/fn-divide]                 ) "selected")
                   :on-click #(re-frame/dispatch [:toggle-current-paint [::dot/fn ::dot/vertical ::dot/fn-divide]])}
          (get @theme [::dot/fn ::dot/vertical ::dot/fn-divide]                 )]
         [:button {:class (when (= @tile-paint [::dot/fn ::dot/vertical ::dot/fn-multiply]               ) "selected")
                   :on-click #(re-frame/dispatch [:toggle-current-paint [::dot/fn ::dot/vertical ::dot/fn-multiply]])}
          (get @theme [::dot/fn ::dot/vertical ::dot/fn-multiply]               )]
         [:button {:class (when (= @tile-paint [::dot/fn ::dot/vertical ::dot/fn-modulus]                ) "selected")
                   :on-click #(re-frame/dispatch [:toggle-current-paint [::dot/fn ::dot/vertical ::dot/fn-modulus]])}
          (get @theme [::dot/fn ::dot/vertical ::dot/fn-mod]                    )]
         [:button {:class (when (= @tile-paint [::dot/fn ::dot/vertical ::dot/fn-exponent]               ) "selected")
                   :on-click #(re-frame/dispatch [:toggle-current-paint [::dot/fn ::dot/vertical ::dot/fn-exponent]])}
          (get @theme [::dot/fn ::dot/vertical ::dot/fn-exponent]               )]
         [:button {:class (when (= @tile-paint [::dot/fn ::dot/vertical ::dot/fn-and]                    ) "selected")
                   :on-click #(re-frame/dispatch [:toggle-current-paint [::dot/fn ::dot/vertical ::dot/fn-and]])}
          (get @theme [::dot/fn ::dot/vertical ::dot/fn-and]                    )]
         [:button {:class (when (= @tile-paint [::dot/fn ::dot/vertical ::dot/fn-or]                     ) "selected")
                   :on-click #(re-frame/dispatch [:toggle-current-paint [::dot/fn ::dot/vertical ::dot/fn-or]])}
          (get @theme [::dot/fn ::dot/vertical ::dot/fn-or]                     )]
         [:button {:class (when (= @tile-paint [::dot/fn ::dot/vertical ::dot/fn-xor]                    ) "selected")
                   :on-click #(re-frame/dispatch [:toggle-current-paint [::dot/fn ::dot/vertical ::dot/fn-xor]])}
          (get @theme [::dot/fn ::dot/vertical ::dot/fn-xor]                    )]
         [:button {:class (when (= @tile-paint [::dot/fn ::dot/vertical ::dot/fn-greater-than]           ) "selected")
                   :on-click #(re-frame/dispatch [:toggle-current-paint [::dot/fn ::dot/vertical ::dot/fn-greater-than]])}
          (get @theme [::dot/fn ::dot/vertical ::dot/fn-greater-than]           )]
         [:button {:class (when (= @tile-paint [::dot/fn ::dot/vertical ::dot/fn-greater-than-or-equal]  ) "selected")
                   :on-click #(re-frame/dispatch [:toggle-current-paint [::dot/fn ::dot/vertical ::dot/fn-greater-than-or-equal]])}
          (get @theme [::dot/fn ::dot/vertical ::dot/fn-greater-than-or-equal]  )]
         [:button {:class (when (= @tile-paint [::dot/fn ::dot/vertical ::dot/fn-equal]                  ) "selected")
                   :on-click #(re-frame/dispatch [:toggle-current-paint [::dot/fn ::dot/vertical ::dot/fn-equal]])}
          (get @theme [::dot/fn ::dot/vertical ::dot/fn-equal]                  )]
         [:button {:class (when (= @tile-paint [::dot/fn ::dot/vertical ::dot/fn-not-equal]              ) "selected")
                   :on-click #(re-frame/dispatch [:toggle-current-paint [::dot/fn ::dot/vertical ::dot/fn-not-equal]])}
          (get @theme [::dot/fn ::dot/vertical ::dot/fn-not-equal]              )]
         [:button {:class (when (= @tile-paint [::dot/fn ::dot/vertical ::dot/fn-less-than-or-equal]        ) "selected")
                   :on-click #(re-frame/dispatch [:toggle-current-paint [::dot/fn ::dot/vertical ::dot/fn-less-than-or-equal]])}
          (get @theme [::dot/fn ::dot/vertical ::dot/fn-less-than-equal]        )]
         [:button {:class (when (= @tile-paint [::dot/fn ::dot/vertical ::dot/fn-less-than]              ) "selected")
                   :on-click #(re-frame/dispatch [:toggle-current-paint [::dot/fn ::dot/vertical ::dot/fn-less-than]])}
          (get @theme [::dot/fn ::dot/vertical ::dot/fn-less-than]              )]]

        [:p "Horizontal Functions"]
        [:div {:class (when (= @tile-paint [::dot/pre-function ::dot/horizontal]) "selected")}
         [:button {:class (when (= @tile-paint [::dot/fn ::dot/horizontal ::dot/fn-add]                  ) "selected")
                   :on-click #(re-frame/dispatch [:toggle-current-paint [::dot/fn ::dot/horizontal ::dot/fn-add]])}
          (get @theme [::dot/fn ::dot/horizontal ::dot/fn-add]                  )]
         [:button {:class (when (= @tile-paint [::dot/fn ::dot/horizontal ::dot/fn-minus]                ) "selected")
                   :on-click #(re-frame/dispatch [:toggle-current-paint [::dot/fn ::dot/horizontal ::dot/fn-minus]])}
          (get @theme [::dot/fn ::dot/horizontal ::dot/fn-minus]                )]
         [:button {:class (when (= @tile-paint [::dot/fn ::dot/horizontal ::dot/fn-divide]               ) "selected")
                   :on-click #(re-frame/dispatch [:toggle-current-paint [::dot/fn ::dot/horizontal ::dot/fn-divide]])}
          (get @theme [::dot/fn ::dot/horizontal ::dot/fn-divide]               )]
         [:button {:class (when (= @tile-paint [::dot/fn ::dot/horizontal ::dot/fn-multiply]             ) "selected")
                   :on-click #(re-frame/dispatch [:toggle-current-paint [::dot/fn ::dot/horizontal ::dot/fn-multiply]])}
          (get @theme [::dot/fn ::dot/horizontal ::dot/fn-multiply]             )]
         [:button {:class (when (= @tile-paint [::dot/fn ::dot/horizontal ::dot/fn-modulus]              ) "selected")
                   :on-click #(re-frame/dispatch [:toggle-current-paint [::dot/fn ::dot/horizontal ::dot/fn-modulus]])}
          (get @theme [::dot/fn ::dot/horizontal ::dot/fn-mod]                  )]
         [:button {:class (when (= @tile-paint [::dot/fn ::dot/horizontal ::dot/fn-exponent]             ) "selected")
                   :on-click #(re-frame/dispatch [:toggle-current-paint [::dot/fn ::dot/horizontal ::dot/fn-exponent]])}
          (get @theme [::dot/fn ::dot/horizontal ::dot/fn-exponent]             )]
         [:button {:class (when (= @tile-paint [::dot/fn ::dot/horizontal ::dot/fn-and]                  ) "selected")
                   :on-click #(re-frame/dispatch [:toggle-current-paint [::dot/fn ::dot/horizontal ::dot/fn-and]])}
          (get @theme [::dot/fn ::dot/horizontal ::dot/fn-and]                  )]
         [:button {:class (when (= @tile-paint [::dot/fn ::dot/horizontal ::dot/fn-or]                   ) "selected")
                   :on-click #(re-frame/dispatch [:toggle-current-paint [::dot/fn ::dot/horizontal ::dot/fn-or]])}
          (get @theme [::dot/fn ::dot/horizontal ::dot/fn-or]                   )]
         [:button {:class (when (= @tile-paint [::dot/fn ::dot/horizontal ::dot/fn-xor]                  ) "selected")
                   :on-click #(re-frame/dispatch [:toggle-current-paint [::dot/fn ::dot/horizontal ::dot/fn-xor]])}
          (get @theme [::dot/fn ::dot/horizontal ::dot/fn-xor]                  )]
         [:button {:class (when (= @tile-paint [::dot/fn ::dot/horizontal ::dot/fn-greater-than]         ) "selected")
                   :on-click #(re-frame/dispatch [:toggle-current-paint [::dot/fn ::dot/horizontal ::dot/fn-greater-than]])}
          (get @theme [::dot/fn ::dot/horizontal ::dot/fn-greater-than]         )]
         [:button {:class (when (= @tile-paint [::dot/fn ::dot/horizontal ::dot/fn-greater-than-or-equal]) "selected")
                   :on-click #(re-frame/dispatch [:toggle-current-paint [::dot/fn ::dot/horizontal ::dot/fn-greater-than-or-equal]])}
          (get @theme [::dot/fn ::dot/horizontal ::dot/fn-greater-than-or-equal])]
         [:button {:class (when (= @tile-paint [::dot/fn ::dot/horizontal ::dot/fn-equal]                ) "selected")
                   :on-click #(re-frame/dispatch [:toggle-current-paint [::dot/fn ::dot/horizontal ::dot/fn-equal]])}
          (get @theme [::dot/fn ::dot/horizontal ::dot/fn-equal]                )]
         [:button {:class (when (= @tile-paint [::dot/fn ::dot/horizontal ::dot/fn-not-equal]            ) "selected")
                   :on-click #(re-frame/dispatch [:toggle-current-paint [::dot/fn ::dot/horizontal ::dot/fn-not-equal]])}
          (get @theme [::dot/fn ::dot/horizontal ::dot/fn-not-equal]            )]
         [:button {:class (when (= @tile-paint [::dot/fn ::dot/horizontal ::dot/fn-less-than-or-equal]      ) "selected")
                   :on-click #(re-frame/dispatch [:toggle-current-paint [::dot/fn ::dot/horizontal ::dot/fn-less-than-or-equal]])}
          (get @theme [::dot/fn ::dot/horizontal ::dot/fn-less-than-or-equal]      )]
         [:button {:class (when (= @tile-paint [::dot/fn ::dot/horizontal ::dot/fn-less-than]            ) "selected")
                   :on-click #(re-frame/dispatch [:toggle-current-paint [::dot/fn ::dot/horizontal ::dot/fn-less-than]])}
          (get @theme [::dot/fn ::dot/horizontal ::dot/fn-less-than]            )]
         ]

        [:p "Dot"]
        [:div {:style {:grid-gap "14px" :padding-top "10px" :padding-bottom "10px"}}
         [:button {:class (when (= @tile-paint [::dot/dot ::dot/left]) " selected")
                   :on-click #(re-frame/dispatch [:toggle-current-paint [::dot/dot ::dot/left]])}
          "⇐"]
         [:button {:class (when (= @tile-paint [::dot/dot ::dot/right]) " selected")
                   :on-click #(re-frame/dispatch [:toggle-current-paint [::dot/dot ::dot/right]])}
          "⇒"]
         [:button {:class (when (= @tile-paint [::dot/dot ::dot/up]) " selected")
                   :on-click #(re-frame/dispatch [:toggle-current-paint [::dot/dot ::dot/up]])}
          "⇑"]
         [:button {:class (when (= @tile-paint [::dot/dot ::dot/down]) " selected")
                   :on-click #(re-frame/dispatch [:toggle-current-paint [::dot/dot ::dot/down]])}
          "⇓"]
         ]
        [:p "Other"]
        [:div
         [:button {:class (when (= @tile-paint [::dot/empty-tile]) " selected")
                   :on-click #(re-frame/dispatch [:toggle-current-paint [::dot/empty-tile]])}
          (get @theme [::dot/empty-tile])]
         [:button {:class (when (= (first @tile-paint) ::dot/literal) " selected")
                   :on-click #(re-frame/dispatch [:toggle-current-paint [::dot/empty-tile]])}
          (if (= (first @tile-paint) ::dot/literal) (second @tile-paint) " ")]
         ]

        ]])))

(defn world-dot
  [id]
  (let [dot (re-frame/subscribe [:dot id])]
    (fn []
      [:div (merge {:class "world-dot"}
                   (grid-coord (::dot/location @dot)))
       (::dot/glyph @dot)])))

(defn world
  []
  (let [[tiles width height] @(re-frame/subscribe [:world])
        dots (re-frame/subscribe [:dot-ids])
        theme @(re-frame/subscribe [:theme])
        ]
    [:div {:class "world"}
     (for [x (range 1 (max (inc width) 30))
           y (range 1 (max (+ height 2) 30))]
       (if-let [tile (get tiles [x y] nil)]
         (tiled-location tile theme)
         (empty-location [x y] (theme [::dot/empty-tile]))))
     (for [did @dots]
       ^{:key (str "dot-" did)}
       [world-dot did])]))

(defn footer
  []
  (let []
    (fn []
      [:div {:class "footer"} "Footer"])))

(defn main-panel []
  (let []
    (fn []
      [:div {:class "player"}
       [top-bar]
       [:div {:class "main-content"}
        [left-bar]
        [world]
        [right-bar]]
       [footer]
       ])))


(defn key-pressed
  [event]
  (let [keycode (.-keyCode event)
        option  (.-metaKey event)
        shift   (.-shiftKey event)
        caught (match [keycode option shift]
                      [45 false false]  [::dot/path ::dot/horizontal]
                      [124 false true]  [::dot/path ::dot/vertical]
                      [43 false true]   [::dot/path ::dot/both]
                      
                      [60 false true]   [::dot/merge ::dot/left]
                      [62 false true]   [::dot/merge ::dot/right]
                      [94 false true]   [::dot/merge ::dot/up]
                      [118 false false] [::dot/merge ::dot/down]
                      [86 false true]   [::dot/merge ::dot/down]
                      
                      [41 false true  ] [::dot/reflect ::dot/left]
                      [40 false true  ] [::dot/reflect ::dot/right]
                      [117 false false] [::dot/reflect ::dot/up]
                      [85 false true  ] [::dot/reflect ::dot/up]
                      [937 false false] [::dot/reflect ::dot/down]
                      [47 false false ] [::dot/reflect ::dot/positive]
                      [92 false false ] [::dot/reflect ::dot/negative]
                      
                      [38 false true ] [::dot/die]
                      [35 false true ] [::dot/value]
                      [64 false true ] [::dot/macro]
                      [36 false true ] [::dot/output]
                      [63 false true ] [::dot/input]
                      [58 false true ] [::dot/pipe]
                      [34 false true ] [::dot/quote]
                      [39 false false] [::dot/quote]
                      [42 false true ] [::dot/duplicate]
                      
                      [126 false true] [::dot/conditional ::dot/horizontal]
                      [96 false true ] [::dot/conditional ::dot/vertical]
                      
                      [123 false true] [::dot/pre-function ::dot/horizontal]
                      [91 false false] [::dot/pre-function ::dot/vertical]

                      [247 false false ] [::dot/fn-stub ::dot/fn-divide]
                      [37 false true   ] [::dot/fn-stub ::dot/fn-modulus]
                      ;; [97 false false  ] [::dot/fn-stub ::dot/fn-and]
                      ;; [111 false false ] [::dot/fn-stub ::dot/fn-or]
                      ;; [120 false false ] [::dot/fn-stub ::dot/fn-xor]
                      [8805 false false] [::dot/fn-stub ::dot/fn-greater-than-or-equal]
                      [8800 false false] [::dot/fn-stub ::dot/fn-not-equal]
                      [8804 false false] [::dot/fn-stub ::dot/fn-less-than-or-equal]
                      [61 false false  ] [::dot/fn-stub ::dot/fn-equal]
                      [8226 false false] [::dot/dot]

                      [_ _ _] [::dot/literal keycode]
                      )
        
        ]
    (when caught
      (re-frame/dispatch [:set-current-paint
                          (if (= (first caught) ::dot/literal)
                            (if (<= 48 (second caught) 57)
                              [::dot/literal (- (second caught) 48)]
                              [::dot/literal (char (second caught))]
                              )
                            caught)]))))

(defn install-key-listeners
  []
  (events/listen js/window EventType.KEYPRESS key-pressed))

(install-key-listeners)
