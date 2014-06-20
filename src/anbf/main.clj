(ns anbf.main
  (:require [clojure.tools.logging :as log]
            [anbf.anbf :refer :all]
            [anbf.game :refer :all]
            [anbf.dungeon :refer :all]
            [anbf.player :refer :all]
            [anbf.tile :refer :all]
            [anbf.handlers :refer :all]
            [anbf.position :refer :all]
            [anbf.pathing :refer :all]
            [anbf.util :refer :all]
            [anbf.jta :refer [raw-write]]
            [anbf.delegator :refer :all]
            [clojure.java.io :as io]
            [cemerick.pomegranate :as pom])
  (:gen-class))

(defn- init-ui [anbf]
  (-> anbf
      (register-handler
        (reify
          GameStateHandler
          (ended [_]
            (log/info "Game ended"))
          (started [_]
            (log/info "Game started"))
          ToplineMessageHandler
          (message [_ text]
            (log/info "Topline message:" text))
          ActionChosenHandler
          (action-chosen [_ action]
            (log/info "Performing action:" action))
          ;BOTLHandler
          ;(botl [_ status]
          ;  (log/info "new botl status:" status))
          ConnectionStatusHandler
          (online [_]
            (log/info "Connection status: online"))
          (offline [_]
            (log/info "Connection status: offline"))
          RedrawHandler
          (redraw [_ frame]
            (println frame))))))

(defn- register-javabot-jars []
  (dorun (map pom/add-classpath
              (filter #(-> % .getName (.endsWith ".jar"))
                      (file-seq (io/file "javabots/bot-jars"))))))

(defn -main [& args] []
  (register-javabot-jars)
  (->> (take 1 args) (apply new-anbf) init-ui start (def a)))

; shorthand functions for REPL use
(defn- w [ch] (raw-write (:jta a) ch))

(defn- r [] (w (ctrl \r)))

(defn- p [] (pause a))

(defn- s [] (stop a))

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
  (print-tiles (partial visible? game) (curlvl game)))

(defn print-fov [game]
  (print-tiles (partial in-fov? game) (curlvl game)))

(defn print-transparent [game]
  (print-tiles transparent? (curlvl game)))

(defn print-path [game path]
  (print-tiles #(.contains path (position %))
               (curlvl game) (constantly \X)))
