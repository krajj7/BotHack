(ns anbf.level
  (:require [clojure.tools.logging :as log]
            [anbf.montype :refer :all]
            [anbf.position :refer :all]
            [anbf.util :refer :all]
            [anbf.tile :refer :all]))

(defrecord Level
  [dlvl
   branch-id
   tags ; subset #{:shop :oracle :minetown :vault :medusa :castle :votd ...}
   blueprint ; for special levels
   tiles
   monsters] ; { {:x X :y Y} => Monster }
  anbf.bot.ILevel)

(defmethod print-method Level [level w]
  (.write w (str "#anbf.level.Level"
                 (assoc (.without level :tiles) :tiles "<trimmed>"))))

(defn- initial-tiles []
  (->> (for [y (range 21)
             x (range 80)]
         (initial-tile x (inc y)))
       (partition 80) (mapv vec)))

(defn new-level [dlvl branch-id]
  (Level. dlvl branch-id #{} nil (initial-tiles) {}))

(defn tile-seq
  "a seq of all 80x20 tiles on the level, left to right, top to bottom"
  [level]
  (apply concat (:tiles level)))

(defn column [level x]
  (for [y (range 1 22)]
    (at level x y)))

(defn shop-inside? [level tile]
  (not-any? door? (including-origin neighbors level tile)))

(def oracle-position {:x 39 :y 12})

(def wiztower-boundary
  (set (concat (for [y (range 5 20)
                     pos [(position 23 y)
                          (position 51 y)]]
                 pos)
               (for [x (range 23 52)
                     pos [(position x 5)
                          (position x 19)]]
                 pos))))

(def wiztower-inner-boundary
  (set (concat (for [y (range 6 19)
                     pos [(position 24 y)
                          (position 51 y)]]
                 pos)
               (for [x (range 24 52)
                     pos [(position x 6)
                          (position x 18)]]
                 pos))))

(def wiztower-rect
  (rectangle (position 23 5) (position 52 20)))

(def air-clouds
  [{:y 2 :x 26} {:y 2 :x 27} {:y 2 :x 28} {:y 2 :x 29} {:y 3 :x 25} {:y 3 :x 26}
   {:y 3 :x 27} {:y 3 :x 28} {:y 3 :x 29} {:y 3 :x 30} {:y 3 :x 39} {:y 3 :x 40}
   {:y 3 :x 41} {:y 3 :x 42} {:y 4 :x 23} {:y 4 :x 24} {:y 4 :x 27} {:y 4 :x 28}
   {:y 4 :x 29} {:y 4 :x 30} {:y 4 :x 31} {:y 4 :x 38} {:y 4 :x 39} {:y 4 :x 40}
   {:y 4 :x 41} {:y 4 :x 42} {:y 4 :x 43} {:y 5 :x 23} {:y 5 :x 24} {:y 5 :x 26}
   {:y 5 :x 27} {:y 5 :x 28} {:y 5 :x 29} {:y 5 :x 30} {:y 5 :x 31} {:y 5 :x 32}
   {:y 5 :x 38} {:y 5 :x 39} {:y 5 :x 40} {:y 5 :x 41} {:y 5 :x 42} {:y 6 :x 24}
   {:y 6 :x 25} {:y 6 :x 26} {:y 6 :x 27} {:y 6 :x 28} {:y 6 :x 29} {:y 6 :x 30}
   {:y 6 :x 31} {:y 6 :x 32} {:y 6 :x 33} {:y 6 :x 39} {:y 6 :x 40} {:y 6 :x 41}
   {:y 6 :x 42} {:y 7 :x 26} {:y 7 :x 27} {:y 7 :x 28} {:y 7 :x 29} {:y 7 :x 33}
   {:y 7 :x 34} {:y 7 :x 38} {:y 7 :x 39} {:y 7 :x 40} {:y 7 :x 41} {:y 8 :x 27}
   {:y 8 :x 28} {:y 8 :x 29} {:y 8 :x 30} {:y 8 :x 37} {:y 8 :x 38} {:y 9 :x 25}
   {:y 9 :x 26} {:y 9 :x 27} {:y 9 :x 28} {:y 9 :x 29} {:y 9 :x 30} {:y 9 :x 31}
   {:y 9 :x 36} {:y 9 :x 37} {:y 9 :x 39} {:y 9 :x 40} {:y 9 :x 41} {:y 9 :x 42}
   {:y 9 :x 43} {:y 9 :x 44} {:y 10 :x 24} {:y 10 :x 25} {:y 10 :x 26}
   {:y 10 :x 27} {:y 10 :x 35} {:y 10 :x 36} {:y 10 :x 38} {:y 10 :x 41}
   {:y 10 :x 42} {:y 10 :x 43} {:y 10 :x 44} {:y 10 :x 45} {:y 11 :x 34}
   {:y 11 :x 35} {:y 11 :x 38} {:y 11 :x 39} {:y 11 :x 40} {:y 11 :x 41}
   {:y 11 :x 42} {:y 11 :x 43} {:y 11 :x 44} {:y 11 :x 45} {:y 11 :x 46}
   {:y 12 :x 34} {:y 12 :x 37} {:y 12 :x 38} {:y 12 :x 39} {:y 12 :x 40}
   {:y 12 :x 44} {:y 12 :x 45} {:y 12 :x 46} {:y 12 :x 47} {:y 12 :x 48}
   {:y 12 :x 49} {:y 13 :x 32} {:y 13 :x 34} {:y 13 :x 36} {:y 13 :x 37}
   {:y 13 :x 38} {:y 13 :x 39} {:y 13 :x 40} {:y 13 :x 44} {:y 13 :x 45}
   {:y 13 :x 46} {:y 13 :x 47} {:y 13 :x 48} {:y 13 :x 49} {:y 13 :x 50}
   {:y 14 :x 31} {:y 14 :x 34} {:y 14 :x 35} {:y 14 :x 36} {:y 14 :x 37}
   {:y 14 :x 38} {:y 14 :x 39} {:y 14 :x 40} {:y 14 :x 45} {:y 14 :x 46}
   {:y 14 :x 47} {:y 14 :x 48} {:y 14 :x 49} {:y 14 :x 50} {:y 14 :x 51}
   {:y 15 :x 32} {:y 15 :x 34} {:y 15 :x 35} {:y 15 :x 36} {:y 15 :x 37}
   {:y 15 :x 38} {:y 15 :x 39} {:y 15 :x 40} {:y 15 :x 41} {:y 15 :x 45}
   {:y 15 :x 46} {:y 15 :x 47} {:y 15 :x 48} {:y 15 :x 49} {:y 15 :x 50}
   {:y 15 :x 51} {:y 15 :x 52} {:y 16 :x 33} {:y 16 :x 34} {:y 16 :x 35}
   {:y 16 :x 36} {:y 16 :x 37} {:y 16 :x 38} {:y 16 :x 39} {:y 16 :x 40}
   {:y 16 :x 41} {:y 16 :x 42} {:y 16 :x 45} {:y 16 :x 46} {:y 16 :x 47}
   {:y 16 :x 48} {:y 16 :x 49} {:y 16 :x 50} {:y 16 :x 51} {:y 16 :x 52}
   {:y 17 :x 31} {:y 17 :x 33} {:y 17 :x 34} {:y 17 :x 35} {:y 17 :x 36}
   {:y 17 :x 37} {:y 17 :x 38} {:y 17 :x 39} {:y 17 :x 40} {:y 17 :x 41}
   {:y 17 :x 42} {:y 17 :x 44} {:y 17 :x 45} {:y 17 :x 46} {:y 17 :x 47}
   {:y 17 :x 48} {:y 17 :x 49} {:y 17 :x 50} {:y 17 :x 51} {:y 18 :x 31}
   {:y 18 :x 34} {:y 18 :x 35} {:y 18 :x 36} {:y 18 :x 37} {:y 18 :x 38}
   {:y 18 :x 39} {:y 18 :x 40} {:y 18 :x 41} {:y 18 :x 42} {:y 18 :x 43}
   {:y 18 :x 44} {:y 18 :x 45} {:y 18 :x 46} {:y 18 :x 47} {:y 18 :x 48}
   {:y 18 :x 49} {:y 19 :x 32} {:y 19 :x 35} {:y 19 :x 36} {:y 19 :x 37}
   {:y 19 :x 38} {:y 19 :x 39} {:y 19 :x 40} {:y 19 :x 44} {:y 19 :x 45}
   {:y 19 :x 46} {:y 19 :x 47} {:y 19 :x 48} {:y 20 :x 30} {:y 20 :x 37}
   {:y 20 :x 38} {:y 20 :x 45} {:y 20 :x 46} {:y 20 :x 47} {:y 20 :x 48}
   {:y 21 :x 45} {:y 21 :x 46} {:y 21 :x 47}])

(def earth-caverns [        [4 3] [5 3] [6 3]
                            [3 4] [4 4] [5 4] [6 4]
                            [3 5] [4 5] [5 5] [6 5] [7 5]
                                  [4 6] [5 6] [6 6] [7 6]
         [55 14] [56 14]                [5 7] [6 7] [7 7] [8 7]
 [54 15] [55 15]                              [6 8] [7 8]
                          [16 10]
                   [15 11][16 11]              [19 11][20 11]
            [14 12][15 12]                     [19 12][20 12][21 12]
            [14 13][15 13][16 13]                     [20 13][21 13][22 13]
                          [16 14][17 14]       [19 14][20 14][21 14]
                                 [17 15][18 15][19 15][20 15]

                                        [23 4] [24 4]
                          [21 5] [22 5] [23 5]
                                 [22 6] [23 6] [24 6] [25 6]
          [28 16] [29 16]               [23 7] [24 7] [25 7]
  [27 17] [28 17]                              [24 8] [25 8]

                                 [42 7] [43 7] [44 7] [45 7]
                   [40 8] [41 8] [42 8] [43 8] [44 8] [45 8] [46 8]
                   [40 9] [41 9]               [44 9] [45 9] [46 9]
            [39 10][40 10]                            [45 10]
            [39 11]                                   [45 11][46 11]
                                                             [46 12]
                          [62 5] [63 5]
                                 [63 6] [64 6] [65 6]
                                 [63 7] [64 7] [65 7]
                                        [64 8]
        [4 18] [5 18] [6 18]                              [72 7]
 [3 19] [4 19] [5 19] [6 19]                       [71 8] [72 8]
               [5 20] [6 20]                       [71 9]
                                                   [71 10][72 10][73 10]
                                                          [72 11]])
(def astral-walls
  [{:y 2 :x 32} {:y 2 :x 33} {:y 2 :x 34} {:y 2 :x 35} {:y 2 :x 36} {:y 2
  :x 37} {:y 2 :x 38} {:y 2 :x 39} {:y 2 :x 40} {:y 2 :x 41} {:y 2 :x 42}
  {:y 2 :x 43} {:y 2 :x 44} {:y 2 :x 45} {:y 2 :x 46} {:y 3 :x 32} {:y 3 :x
  46} {:y 4 :x 32} {:y 4 :x 35} {:y 4 :x 36} {:y 4 :x 37} {:y 4 :x 38} {:y
  4 :x 39} {:y 4 :x 40} {:y 4 :x 41} {:y 4 :x 42} {:y 4 :x 43} {:y 4 :x 46}
  {:y 5 :x 32} {:y 5 :x 35} {:y 5 :x 43} {:y 5 :x 46} {:y 6 :x 2} {:y 6 :x
  3} {:y 6 :x 4} {:y 6 :x 5} {:y 6 :x 6} {:y 6 :x 7} {:y 6 :x 8} {:y 6 :x
  9} {:y 6 :x 10} {:y 6 :x 11} {:y 6 :x 12} {:y 6 :x 13} {:y 6 :x 14} {:y 6
  :x 15} {:y 6 :x 16} {:y 6 :x 32} {:y 6 :x 35} {:y 6 :x 43} {:y 6 :x 46}
  {:y 6 :x 62} {:y 6 :x 63} {:y 6 :x 64} {:y 6 :x 65} {:y 6 :x 66} {:y 6 :x
  67} {:y 6 :x 68} {:y 6 :x 69} {:y 6 :x 70} {:y 6 :x 71} {:y 6 :x 72} {:y
  6 :x 73} {:y 6 :x 74} {:y 6 :x 75} {:y 6 :x 76} {:y 7 :x 2} {:y 7 :x 16}
  {:y 7 :x 32} {:y 7 :x 35} {:y 7 :x 43} {:y 7 :x 46} {:y 7 :x 62} {:y 7 :x
  76} {:y 8 :x 2} {:y 8 :x 5} {:y 8 :x 6} {:y 8 :x 7} {:y 8 :x 8} {:y 8 :x
  9} {:y 8 :x 10} {:y 8 :x 11} {:y 8 :x 12} {:y 8 :x 13} {:y 8 :x 16} {:y 8
  :x 17} {:y 8 :x 21} {:y 8 :x 22} {:y 8 :x 23} {:y 8 :x 24} {:y 8 :x 25}
  {:y 8 :x 26} {:y 8 :x 27} {:y 8 :x 28} {:y 8 :x 29} {:y 8 :x 32} {:y 8 :x
  35} {:y 8 :x 43} {:y 8 :x 46} {:y 8 :x 49} {:y 8 :x 50} {:y 8 :x 51} {:y
  8 :x 52} {:y 8 :x 53} {:y 8 :x 54} {:y 8 :x 55} {:y 8 :x 56} {:y 8 :x 57}
  {:y 8 :x 61} {:y 8 :x 62} {:y 8 :x 65} {:y 8 :x 66} {:y 8 :x 67} {:y 8 :x
  68} {:y 8 :x 69} {:y 8 :x 70} {:y 8 :x 71} {:y 8 :x 72} {:y 8 :x 73} {:y
  8 :x 76} {:y 9 :x 2} {:y 9 :x 5} {:y 9 :x 13} {:y 9 :x 17} {:y 9 :x 18}
  {:y 9 :x 20} {:y 9 :x 21} {:y 9 :x 29} {:y 9 :x 30} {:y 9 :x 32} {:y 9 :x
  35} {:y 9 :x 43} {:y 9 :x 46} {:y 9 :x 48} {:y 9 :x 49} {:y 9 :x 57} {:y
  9 :x 58} {:y 9 :x 60} {:y 9 :x 61} {:y 9 :x 65} {:y 9 :x 73} {:y 9 :x 76}
  {:y 10 :x 2} {:y 10 :x 5} {:y 10 :x 13} {:y 10 :x 18} {:y 10 :x 19} {:y
  10 :x 20} {:y 10 :x 30} {:y 10 :x 31} {:y 10 :x 32} {:y 10 :x 35} {:y 10
  :x 36} {:y 10 :x 37} {:y 10 :x 38} {:y 10 :x 40} {:y 10 :x 41} {:y 10 :x
  42} {:y 10 :x 43} {:y 10 :x 46} {:y 10 :x 47} {:y 10 :x 48} {:y 10 :x 58}
  {:y 10 :x 59} {:y 10 :x 60} {:y 10 :x 65} {:y 10 :x 73} {:y 10 :x 76} {:y
  11 :x 2} {:y 11 :x 5} {:y 11 :x 31} {:y 11 :x 32} {:y 11 :x 46} {:y 11 :x
  47} {:y 11 :x 73} {:y 11 :x 76} {:y 12 :x 2} {:y 12 :x 5} {:y 12 :x 13}
  {:y 12 :x 18} {:y 12 :x 19} {:y 12 :x 20} {:y 12 :x 30} {:y 12 :x 31} {:y
  12 :x 32} {:y 12 :x 33} {:y 12 :x 34} {:y 12 :x 44} {:y 12 :x 45} {:y 12
  :x 46} {:y 12 :x 47} {:y 12 :x 48} {:y 12 :x 58} {:y 12 :x 59} {:y 12 :x
  60} {:y 12 :x 65} {:y 12 :x 73} {:y 12 :x 76} {:y 13 :x 2} {:y 13 :x 5}
  {:y 13 :x 13} {:y 13 :x 17} {:y 13 :x 18} {:y 13 :x 20} {:y 13 :x 21} {:y
  13 :x 29} {:y 13 :x 30} {:y 13 :x 34} {:y 13 :x 35} {:y 13 :x 36} {:y 13
  :x 37} {:y 13 :x 38} {:y 13 :x 40} {:y 13 :x 41} {:y 13 :x 42} {:y 13 :x
  43} {:y 13 :x 44} {:y 13 :x 48} {:y 13 :x 49} {:y 13 :x 57} {:y 13 :x 58}
  {:y 13 :x 60} {:y 13 :x 61} {:y 13 :x 65} {:y 13 :x 73} {:y 13 :x 76} {:y
  14 :x 2} {:y 14 :x 5} {:y 14 :x 6} {:y 14 :x 7} {:y 14 :x 8} {:y 14 :x 9}
  {:y 14 :x 10} {:y 14 :x 11} {:y 14 :x 12} {:y 14 :x 13} {:y 14 :x 16} {:y
  14 :x 17} {:y 14 :x 21} {:y 14 :x 22} {:y 14 :x 23} {:y 14 :x 24} {:y 14
  :x 26} {:y 14 :x 27} {:y 14 :x 28} {:y 14 :x 29} {:y 14 :x 34} {:y 14 :x
  35} {:y 14 :x 43} {:y 14 :x 44} {:y 14 :x 49} {:y 14 :x 50} {:y 14 :x 51}
  {:y 14 :x 52} {:y 14 :x 54} {:y 14 :x 55} {:y 14 :x 56} {:y 14 :x 57} {:y
  14 :x 61} {:y 14 :x 62} {:y 14 :x 65} {:y 14 :x 66} {:y 14 :x 67} {:y 14
  :x 68} {:y 14 :x 69} {:y 14 :x 70} {:y 14 :x 71} {:y 14 :x 72} {:y 14 :x
  73} {:y 14 :x 76} {:y 15 :x 2} {:y 15 :x 16} {:y 15 :x 23} {:y 15 :x 27}
  {:y 15 :x 28} {:y 15 :x 29} {:y 15 :x 30} {:y 15 :x 31} {:y 15 :x 32} {:y
  15 :x 33} {:y 15 :x 34} {:y 15 :x 44} {:y 15 :x 45} {:y 15 :x 46} {:y 15
  :x 47} {:y 15 :x 48} {:y 15 :x 49} {:y 15 :x 50} {:y 15 :x 51} {:y 15 :x
  55} {:y 15 :x 62} {:y 15 :x 76} {:y 16 :x 2} {:y 16 :x 3} {:y 16 :x 4} {:y
  16 :x 5} {:y 16 :x 6} {:y 16 :x 7} {:y 16 :x 8} {:y 16 :x 9} {:y 16 :x
  10} {:y 16 :x 11} {:y 16 :x 12} {:y 16 :x 13} {:y 16 :x 14} {:y 16 :x 15}
  {:y 16 :x 16} {:y 16 :x 23} {:y 16 :x 33} {:y 16 :x 45} {:y 16 :x 55} {:y
  16 :x 62} {:y 16 :x 63} {:y 16 :x 64} {:y 16 :x 65} {:y 16 :x 66} {:y 16
  :x 67} {:y 16 :x 68} {:y 16 :x 69} {:y 16 :x 70} {:y 16 :x 71} {:y 16 :x
  72} {:y 16 :x 73} {:y 16 :x 74} {:y 16 :x 75} {:y 16 :x 76} {:y 17 :x 23}
  {:y 17 :x 24} {:y 17 :x 25} {:y 17 :x 26} {:y 17 :x 27} {:y 17 :x 28} {:y
  17 :x 29} {:y 17 :x 33} {:y 17 :x 34} {:y 17 :x 44} {:y 17 :x 45} {:y 17
  :x 49} {:y 17 :x 50} {:y 17 :x 51} {:y 17 :x 52} {:y 17 :x 53} {:y 17 :x
  54} {:y 17 :x 55} {:y 18 :x 29} {:y 18 :x 34} {:y 18 :x 35} {:y 18 :x 43}
  {:y 18 :x 44} {:y 18 :x 49} {:y 19 :x 29} {:y 19 :x 30} {:y 19 :x 31} {:y
  19 :x 35} {:y 19 :x 36} {:y 19 :x 37} {:y 19 :x 38} {:y 19 :x 40} {:y 19
  :x 41} {:y 19 :x 42} {:y 19 :x 43} {:y 19 :x 47} {:y 19 :x 48} {:y 19 :x
  49} {:y 20 :x 31} {:y 20 :x 47} {:y 21 :x 31} {:y 21 :x 32} {:y 21 :x 33}
  {:y 21 :x 34} {:y 21 :x 35} {:y 21 :x 36} {:y 21 :x 37} {:y 21 :x 38} {:y
  21 :x 39} {:y 21 :x 40} {:y 21 :x 41} {:y 21 :x 42} {:y 21 :x 43} {:y 21
  :x 44} {:y 21 :x 45} {:y 21 :x 46} {:y 21 :x 47}])

(def geh-maze {:cutoff-cols [0 1 77 78 79]
               :cutoff-rows [1 2 3 21]})

(def soko-2a-holes [{:y 16 :x 38} {:y 16 :x 39} {:y 16 :x 40} {:y 16 :x 41} {:y 16 :x 42} {:y 16 :x 43} {:y 16 :x 44} {:y 16 :x 45} {:y 16 :x 46} {:y 16 :x 47} {:y 16 :x 48} {:y 16 :x 49}])
; 2b not necessary, holes initially out of LOS
(def soko-3a-holes [{:y 17 :x 37} {:y 17 :x 38} {:y 17 :x 39} {:y 17 :x 40} {:y 17 :x 41} {:y 17 :x 42} {:y 17 :x 43} {:y 17 :x 44} {:y 17 :x 45} {:y 17 :x 46} {:y 17 :x 47}])
(def soko-3b-holes [{:y 15 :x 38} {:y 15 :x 39} {:y 15 :x 40} {:y 15 :x 41} {:y 15 :x 42} {:y 15 :x 43} {:y 15 :x 44} {:y 15 :x 45} {:y 15 :x 46} {:y 15 :x 47}])
(def soko-4a-holes [{:y 5 :x 34} {:y 5 :x 35} {:y 5 :x 36} {:y 5 :x 37} {:y 5 :x 38} {:y 5 :x 39} {:y 5 :x 40} {:y 5 :x 41} {:y 5 :x 42} {:y 5 :x 43} {:y 5 :x 44} {:y 5 :x 45} {:y 5 :x 46} {:y 5 :x 47} {:y 5 :x 48} {:y 5 :x 49}])
; 4b not necessary, holes initially out of LOS

(def blueprints
  [{:branch :sokoban
    :tag :soko-2a
    :features (zipmap soko-2a-holes (repeat :hole))}
   {:branch :sokoban
    :tag :soko-3a
    :features (zipmap soko-3a-holes (repeat :hole))}
   {:branch :sokoban
    :tag :soko-3b
    :features (zipmap soko-3b-holes (repeat :hole))}
   {:branch :sokoban
    :tag :soko-4a
    :features (zipmap soko-4a-holes (repeat :hole))}
   {:branch :astral
    :features (merge (zipmap astral-walls (repeat :wall))
                     (zipmap [{:x 39 :y 7} {:x 9 :y 11} {:x 69 :y 11}]
                             (repeat :altar))
                     (zipmap [{:y 10, :x 39} {:y 11, :x 13} {:y 11, :x 19}
                              {:y 11, :x 59} {:y 11, :x 65} {:y 13, :x 39}
                              {:y 14, :x 25} {:y 14, :x 53} {:y 19, :x 39}]
                             (repeat :door-closed)))}
   {:branch :earth
    :features (into {} (for [[x y] earth-caverns]
                         [(position x y) :floor]))}
   {:branch :air
    :cutoff-cols [0 1 78 79]
    :cutoff-rows [1]
    :features (zipmap air-clouds (repeat :cloud))}
   {:branch :water
    :cutoff-cols [0 1 78 79]
    :cutoff-rows [1]}
   {:branch :main
    :tag :medusa-1
    :cutoff-rows [1]
    :cutoff-cols [0 1 77 78 79]
    :monsters {{:x 38 :y 12} (name->monster "Medusa")}
    :features {{:x 38 :y 12} :stairs-down
               {:x 32 :y 14} :door-secret
               {:x 48 :y 9} :door-secret
               {:x 40 :y 14} :squeaky
               {:x 40 :y 9} :squeaky}}
   {:branch :main
    :tag :medusa-2
    :monsters {{:x 70 :y 12} (name->monster "Medusa")}
    :features {{:x 70 :y 12} :stairs-down
               {:x 3 :y 7} :door-secret
               {:x 8 :y 16} :door-secret
               {:x 62 :y 7} :door-secret
               {:x 68 :y 10} :door-secret
               {:x 75 :y 16} :door-secret
               {:x 5 :y 14} :magictrap}}
   {:branch :main
    :tag :castle
    :undiggable true
    :undiggable-floor true
    :cutoff-rows [1 2]
    :cutoff-cols [0 78 79]
    :features {{:x 55 :y 11} :door-secret
               {:x 54 :y 9} :door-secret
               {:x 56 :y 9} :door-secret
               {:x 55 :y 13} :door-secret
               {:x 56 :y 15} :door-secret
               {:x 54 :y 15} :door-secret
               {:x 46 :y 12} :door-secret
               {:x 48 :y 12} :trapdoor
               {:x 52 :y 12} :trapdoor
               {:x 56 :y 12} :trapdoor
               {:x 60 :y 12} :trapdoor
               {:x 63 :y 12} :trapdoor}}
   {:branch :main
    :tag :votd
    :undiggable true
    :undiggable-floor true
    :features {{:x 68 :y 19} :stairs-up
               {:x 6 :y 3} :door-secret
               {:x 10 :y 6} :door-secret
               {:x 8 :y 8} :door-secret}}
   {:branch :main
    :undiggable true
    :tag :asmodeus
    :features {{:x 28 :y 8} :door-secret
               {:x 19 :y 8} :spikepit
               {:x 17 :y 16} :door-secret
               {:x 20 :y 12} :door-secret
               {:x 22 :y 12} :firetrap
               {:x 27 :y 13} :stairs-down}}
   {:branch :main
    :undiggable true
    :tag :baalzebub
    :features {{:x 38 :y 12} :door-secret
               {:x 57 :y 11} :door-secret
               {:x 62 :y 14} :door-secret
               {:x 70 :y 12} :door-secret}}
   {:branch :main
    :tag :wiztower-level
    :features (into {} (for [pos wiztower-inner-boundary] [pos :rock]))
    :undiggable-tiles wiztower-inner-boundary}
   {:branch :main
    :tag :sanctum
    :cutoff-rows [1 2 3 4 5 20 21]
    :cutoff-cols [0 1 2 3 4 71 72 73 74 75 76 77 78 79]
    :features (into {{:x 20 :y 10} :altar
                     {:x 56 :y 14} :door-secret
                     ;{:x 59 :y 12} :door-secret ; better not to know about this useless dead-end
                     {:x 63 :y 12} :door-secret
                     {:x 66 :y 14} :door-secret
                     {:x 37 :y 9} :door-secret}
                    (concat (for [pos (rectangle-boundary {:x 16 :y 8}
                                                          {:x 24 :y 13})]
                              [pos :wall])
                            (for [pos (rectangle-boundary {:x 15 :y 7}
                                                          {:x 25 :y 14})]
                              [pos :firetrap])))}
   {:branch :main
    :tag :fake-wiztower
    :features {{:x 38 :y 12} :portal
               {:x 39 :y 12} :squeaky
               {:x 37 :y 12} :squeaky
               {:x 38 :y 13} :squeaky
               {:x 38 :y 11} :squeaky}}
   {:branch :quest
    :role :valkyrie
    :dlvl "Home 1"
    :leader {:x 37 :y 12}
    :monsters {{:x 37 :y 12} (name->monster "Norn")}
    :cutoff-rows [1 2]
    :cutoff-cols [0 1 78 79]}
   {:branch :quest
    :role :valkyrie
    :dlvl "Home 2"
    :cutoff-cols [79]}
   {:branch :quest
    :role :valkyrie
    :dlvl "Home 3"
    :cutoff-cols [79]}
   {:branch :quest
    :role :valkyrie
    :dlvl "Home 4"
    :cutoff-cols [79]}
   {:branch :quest
    :role :valkyrie
    :dlvl "Home 5"
    :cutoff-cols [79]}
   {:branch :quest
    :role :valkyrie
    :dlvl "Home 6"
    :cutoff-cols [79]}
   {:branch :mines
    :tag :minetown-grotto
    :features {{:x 48 :y 4} :stairs-down}}
   {:branch :mines
    :tag :minesend-1
    :undiggable true
    :features {{:x 52 :y 7} :door-secret
               {:x 68 :y 6} :door-secret
               {:x 28 :y 12} :door-secret
               {:x 23 :y 12} :door-secret
               {:x 42 :y 18} :door-secret
               {:x 53 :y 20} :door-secret}}
   {:branch :vlad
    :tag :bottom
    :features {{:x 20 :y 13} :door-secret
               {:x 25 :y 14} :door-secret
               {:x 29 :y 14} :door-secret
               {:x 30 :y 13} :door-secret
               {:x 30 :y 9} :door-secret
               {:x 29 :y 8} :door-secret
               {:x 25 :y 8} :door-secret
               {:x 21 :y 8} :door-secret
               {:x 20 :y 9} :door-secret
               {:x 21 :y 14} :door-secret}}
   {:branch :vlad
    :tag :middle
    :features {{:x 18 :y 9} :door-secret
               {:x 19 :y 8} :door-secret
               {:x 23 :y 8} :door-secret
               {:x 27 :y 8} :door-secret
               {:x 28 :y 9} :door-secret
               {:x 28 :y 13} :door-secret
               {:x 27 :y 14} :door-secret
               {:x 23 :y 14} :door-secret
               {:x 19 :y 14} :door-secret
               {:x 18 :y 13} :door-secret}}
   {:branch :vlad
    :tag :end
    :features {{:x 19 :y 8} :door-secret
               {:x 23 :y 8} :door-secret
               {:x 27 :y 8} :door-secret
               {:x 27 :y 14} :door-secret
               {:x 23 :y 14} :door-secret
               {:x 19 :y 14} :door-secret}}
   {:branch :wiztower
    :tag :bottom
    :cutoff-rows (concat (range 1 5) (range 19 22))
    :cutoff-cols (concat (range 0 22) (range 52 80))
    :undiggable-tiles (remove (set (rectangle (position 32 10)
                                              (position 38 16)))
                              wiztower-rect)
    :features {{:x 43 :y 17} :door-secret
               {:x 42 :y 11} :door-secret
               {:x 40 :y 7} :door-secret
               {:x 48 :y 8} :door-secret
               {:x 27 :y 10} :door-secret
               {:x 30 :y 16} :door-secret
               {:x 35 :y 13} :stairs-up
               {:x 34 :y 13} :squeaky
               {:x 36 :y 13} :squeaky
               {:x 35 :y 12} :squeaky
               {:x 35 :y 14} :squeaky}}
   {:branch :wiztower
    :tag :middle
    :undiggable true
    :cutoff-rows (concat (range 1 5) (range 19 22))
    :cutoff-cols (concat (range 0 22) (range 52 80))
    :features {{:x 42 :y 17} :door-secret
               {:x 43 :y 14} :door-secret
               {:x 46 :y 14} :door-secret
               {:x 35 :y 16} :door-secret
               {:x 30 :y 16} :door-secret
               {:x 48 :y 8} :door-secret
               {:x 40 :y 8} :door-secret
               {:x 49 :y 11} :door-secret
               {:x 26 :y 13} :door-secret
               {:x 28 :y 10} :door-secret
               {:x 31 :y 10} :door-secret
               {:x 32 :y 7} :door-secret}}
   {:branch :wiztower
    :tag :end
    :cutoff-rows (concat (range 1 5) (range 19 22))
    :cutoff-cols (concat (range 0 23) (range 52 80))
    :undiggable-tiles (remove (set (rectangle (position 37 8) (position 43 14)))
                              wiztower-rect)
    :features {{:x 29 :y 9} :door-secret
               {:x 32 :y 8} :door-secret
               {:x 27 :y 14} :door-secret
               {:x 35 :y 17} :door-secret
               {:x 47 :y 16} :door-secret
               {:x 49 :y 16} :door-secret
               {:x 49 :y 9} :door-secret
               {:x 41 :y 11} :squeaky
               {:x 39 :y 11} :squeaky
               {:x 40 :y 12} :squeaky
               {:x 40 :y 10} :squeaky}}
   {:role :samurai
    :dlvl "Home 3"
    :branch :quest
    :features {{:x 28 :y 15} :door-secret
               {:x 51 :y 8} :door-secret}}
   {:role :samurai
    :dlvl "Home 1"
    :undiggable true
    :branch :quest
    :leader {:y 6, :x 22}
    :monsters {{:y 6, :x 22} (name->monster "Lord Sato")}
    :features {{:x 29 :y 6} :door-secret
               {:x 52 :y 6} :door-secret}}])
