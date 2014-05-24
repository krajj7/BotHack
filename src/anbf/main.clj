(ns anbf.main
  (:require [clojure.tools.logging :as log]
            [anbf.anbf :refer :all]
            [anbf.game :refer :all]
            [anbf.dungeon :refer :all]
            [anbf.position :refer :all]
            [anbf.pathing :refer :all]
            [anbf.util :refer :all]
            [anbf.jta :refer [raw-write]]
            [anbf.delegator :refer :all]
            [clojure.java.io :as io]
            [cemerick.pomegranate :as pom])
  (:gen-class))

(defn- init-ui [anbf]
  (register-handler anbf (reify RedrawHandler
                           (redraw [_ frame]
                             (println frame)))))

(defn- register-javabot-jars []
  (dorun (map pom/add-classpath
              (filter #(-> % .getName (.endsWith ".jar"))
                      (file-seq (io/file "javabots/bot-jars"))))))

(defn -main [& args] []
  (register-javabot-jars)
  (->> (take 1 args) (apply new-anbf) init-ui start (def a)))

; shorthand functions for REPL use
(defn- w
  [ch]
  (raw-write (:jta a) ch))

(defn- p []
  (pause a))

(defn- s []
  (stop a))

(defn- u []
  (if (:inhibited @(:delegator a))
    (unpause a)))

(defn print-tiles
  "Print map, with pred overlayed with X where pred is not true for the tile. If f is supplied print (f tile) for matching tiles, else the glyph."
  ([level]
   (print-tiles (constantly true) level))
  ([pred level]
   (print-tiles pred level nil))
  ([pred level f]
   (dorun (map (fn [row]
                 (dorun (map (fn [tile]
                               (print (if (pred tile)
                                        (if f (f tile) (:glyph tile))
                                        (if f (:glyph tile) \X))))
                             row))
                 (println))
               (:tiles level)))))

(defn print-los [game]
  (print-tiles (partial visible? game) (curlvl (:dungeon game))))

(defn print-fov [game]
  (print-tiles (partial in-fov? game) (curlvl (:dungeon game))))

(defn print-transparent [game]
  (print-tiles transparent? (curlvl (:dungeon game))))

(defn print-path [game path]
  (print-tiles #(.contains path (position %))
               (curlvl (:dungeon game)) (constantly \X)))
