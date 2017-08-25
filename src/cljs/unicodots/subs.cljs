(ns unicodots.subs
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [re-frame.core :as re-frame]))

(re-frame/reg-sub
 :name
 (fn [db]
   (:name db)))

(re-frame/reg-sub
 :world
 (fn [db]
   (let [world (:world db)
         k (keys world)
         height (reduce max (map second k))
         width (reduce max (map first k))
         ]
     [world width height])))

(re-frame/reg-sub
 :input
 (fn [db]
   (:input db)))

(re-frame/reg-sub
 :output
 (fn [db]
   (:output db)))



(re-frame/reg-sub
 :dot-ids
 (fn [db]
   (keys (:dots db))))

(re-frame/reg-sub
 :dot
 (fn [db [_ id]]
   (get-in db [:dots id])))

(re-frame/reg-sub
 :step-delay-val
 (fn [db _]
   (/ (.log js/Math (/ (get db :step-delay) 50)) (.log js/Math 10))))

(re-frame/reg-sub
 :theme
 (fn [db _]
   (get db :theme)))

(re-frame/reg-sub
 :current-paint
 (fn [db _]
   (get db :current-paint)))
