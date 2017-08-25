(ns unicodots.css
  (:require [garden.def :refer [defstyles defkeyframes]]))

(defkeyframes dotpulse
  [:from {:transform "scale(0.75)"
          :z-index -2}]
  [:to {:transform "scale(1.25)"
        :z-index 2}])

(defn red-border
  [m]
  (merge m {:border-style "solid"
            :border-width "1px"
            :border-color "red"}))

(def world-class
  [:.world
   {:display :grid
    :grid-auto-columns "20px"
    :grid-gap "2px"
    :grid-auto-rows "20px"

    :padding "20px"
    :overflow :scroll}
   [:div {:text-align "center" :z-index 0}]
   [:div:hover {:border-style "solid"
                :border-width "1px"
                :border-color "black"}]])

(def left-palettes
  [[:.palettes
   {:display :flex
    :flex-direction :column
    :justify-content :flex-start
    :min-width "240px"
    }
   [:p {:text-align :center}]
   [:div {:padding-left "10%"
          :padding-right "10%"
          :border-style :solid
          :border-color "black"
          :border-width "1px"
          :display :grid
          :grid-template-columns "repeat(5, 36px)"
          :grid-auto-rows "36px"
          :grid-gap "4px"
          }
    [:div {:border-style :none}]
    ]]
   [:button.selected {:border-style :solid :border-color "#222222" :border-width "2px"}]
   [:div.selected {:background-color "#AAAAAA"}]])

(def player-class
  [:div.player
   {:display :flex
    :flex-direction :column
    :justify-content :space-between}])

(def nav-bars
  (list
   [:div.top-bar
    [:div {:display :inline}]
    (red-border
     {:min-height "40px"})]
   [:div.left-bar
    (red-border {:min-width "80px"})]
   [:div.right-bar
    (red-border
     {:padding-right "20px"
      :min-width "200px"
      :display :flex
      :flex-direction :column
      :justify-content :flex-start
      :align-items :center
      :text-align :center})
    [:.inputs
     [:input {:width "100%" :min-height "100px"}]]
    [:.outputs
     [:.p-container
      {
       :min-width "300px" :overflow "scroll" :max-height "300px"
       :border-style :solid
       :border-width "1px"
       :border-color "black"}]]]
   [:div.footer
    (red-border {})]))

(def dot-class
  [:.world-dot
   {:animation [[dotpulse "1s" :infinite :alternate]]}])

(defstyles screen
  [[:#app {:padding-left "20px" :padding-right "20px"}]
   world-class
   player-class
   nav-bars
   dotpulse
   dot-class
   left-palettes
   [:div.main-content
    {:display :flex
     :flex-direction :row
     :justify-content :space-between}]
   ])


