(ns unicodots.db
  (:require [unicodots.dot :as dot]))

(def default-db
  {:world {
           [3 1] #::dot{:location-type [::dot/reflect ::dot/positive] :location [3 1]}
           [4 1] #::dot{:location-type [::dot/literal 1] :location [4 1]}
           [5 1] #::dot{:location-type [::dot/value] :location [5 1]}
           [6 1] #::dot{:location-type [::dot/path ::dot/horizontal] :location [6 1]}

           [3 2] #::dot{:location-type [::dot/path ::dot/vertical] :location [3 2]}

           [1 3] #::dot{:location-type [::dot/reflect ::dot/positive] :location [1 3]}
           [2 3] #::dot{:location-type [::dot/path ::dot/horizontal] :location [2 3]}
           [3 3] #::dot{:location-type [::dot/path ::dot/both] :location [3 3]}
           [4 3] #::dot{:location-type [::dot/path ::dot/horizontal] :location [4 3]}
           [5 3] #::dot{:location-type [::dot/output] :location [5 3]}
           [6 3] #::dot{:location-type [::dot/value] :location [6 3]}
           [7 3] #::dot{:location-type [::dot/reflect ::dot/negative] :location [7 3]}

           [1 4] #::dot{:location-type [::dot/path ::dot/vertical] :location [1 4]}
           [3 4] #::dot{:location-type [::dot/path ::dot/vertical] :location [3 4]}
           [7 4] #::dot{:location-type [::dot/path ::dot/vertical] :location [7 4]}

           [1 5] #::dot{:location-type [::dot/fn ::dot/vertical ::dot/fn-add] :location [1 5]
                  :horizontal-queue #queue []
                  :vertical-queue   #queue []}
           [2 5] #::dot{:location-type [::dot/path ::dot/horizontal] :location [2 5]}
           [3 5] #::dot{:location-type [::dot/merge ::dot/left] :location [3 5]}
           [4 5] #::dot{:location-type [::dot/literal 1] :location [4 5]}
           [5 5] #::dot{:location-type [::dot/value] :location [5 5]}
           [6 5] #::dot{:location-type [::dot/path ::dot/horizontal] :location [6 5]}
           [7 5] #::dot{:location-type [::dot/duplicate] :location [7 5]}

           [1 6] #::dot{:location-type [::dot/path ::dot/vertical] :location [1 6]}
           [7 6] #::dot{:location-type [::dot/path ::dot/vertical] :location [7 6]}

           [1 7] #::dot{:location-type [::dot/reflect ::dot/negative] :location [1 7]}
           [2 7] #::dot{:location-type [::dot/path ::dot/horizontal] :location [2 7]}
           [3 7] #::dot{:location-type [::dot/path ::dot/horizontal] :location [3 7]}
           [4 7] #::dot{:location-type [::dot/merge ::dot/left] :location [4 7]}
           [5 7] #::dot{:location-type [::dot/path ::dot/horizontal] :location [5 7]}
           [6 7] #::dot{:location-type [::dot/path ::dot/horizontal] :location [6 7]}
           [7 7] #::dot{:location-type [::dot/reflect ::dot/positive] :location [7 7]}

           [4 8] #::dot{:location-type [::dot/path ::dot/vertical] :location [4 8]}
           [4 9] #::dot{:location-type [::dot/literal 0] :location [4 9]}
           [4 10] #::dot{:location-type [::dot/value] :location [4 10]}
           [4 11] #::dot{:location-type [::dot/path ::dot/vertical] :location [4 11]}
           [4 12] #::dot{:location-type [::dot/path ::dot/vertical] :location [4 12]}
           }
   :dots  {1
           #::dot{:id 1
                  :glyph "🍔"
                  :value 0
                  :location [4 12]
                  :dir ::dot/up
                  :state [::dot/moving]
                  :macro-stack []}
           2
           #::dot{:id 2
                  :glyph "🍕"
                  :value 0
                  :location [6 1]
                  :dir ::dot/left
                  :state [::dot/moving]
                  :macro-stack []}
           }
   :step-delay 500
   :ready-fns #queue []
   :next-dot-id 3
   :input "foo\nbar"
   :input-queue #queue []
   :output ["Example" "Output"]
   :theme {[::dot/empty-tile] "■"
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
           [::dot/fn ::dot/vertical ::dot/fn-less-than-equal]         "[≤]"
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
           ::dot/dot "🍕"
           :dot-seeds ["🍕" "🍔" "🐸"] }
   })
