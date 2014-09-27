(ns anbf.main
  (:refer-clojure :exclude [==])
  (:require [clojure.tools.logging :as log]
            [clojure.pprint :refer [pprint]]
            [clojure.core.logic :refer :all]
            [clojure.core.logic.pldb :as pldb]
            [anbf.anbf :refer :all]
            [anbf.actions :refer :all]
            [anbf.game :refer :all]
            [anbf.dungeon :refer :all]
            [anbf.item :refer :all]
            [anbf.itemid :refer :all]
            [anbf.itemtype :refer :all]
            [anbf.level :refer :all]
            [anbf.player :refer :all]
            [anbf.tile :refer :all]
            [anbf.handlers :refer :all]
            [anbf.frame :refer :all]
            [anbf.fov :refer :all]
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
          MultilineMessageHandler
          (message-lines [_ lines]
            (log/info "Multiline message:" (with-out-str (pprint lines))))
          FoundItemsHandler
          (found-items [_ items]
            (log/info "Found items:" items))
          AboutToChooseActionHandler
          (about-to-choose [_ game]
            (log/info "current updated tile:" (at-player game)))
          ActionChosenHandler
          (action-chosen [_ action]
            (log/info "Performing action:" (dissoc action :reason)
                      "\n reasons:\n" (with-out-str (pprint (:reason action)))))
          InventoryHandler
          (inventory-list [_ inventory]
            (log/spy inventory))
          KnowPositionHandler
          (know-position [_ frame]
            (log/info "current player position:" (:cursor frame))
            (log/info "current tile (not yet updated):" (at-player @(:game anbf))))
          BOTLHandler
          (botl [_ status]
            (log/info "new botl status:" status))
          DlvlChangeHandler
          (dlvl-changed [_ old-dlvl new-dlvl]
            (log/info "dlvl changed from" old-dlvl "to" new-dlvl))
          ConnectionStatusHandler
          (online [_]
            (log/info "Connection status: online"))
          (offline [_]
            (log/info "Connection status: offline"))
          RedrawHandler
          (redraw [_ frame]
            (println frame))))))

(defn- register-javabot-jars []
  (doseq [l (file-seq (io/file "javabots/bot-jars"))
          :when (-> l .getName (.endsWith ".jar"))]
    (pom/add-classpath l)))

(defn -main [& args] []
  (register-javabot-jars)
  (->> (take 1 args) (apply new-anbf) init-ui start (def a)))

; shorthand functions for REPL use
(defn- w [ch] (raw-write (:jta a) ch))

(defn- r [] (w (ctrl \r)))

(defn- p [] (pause a))

(defn- s [] (stop a))

(defn- u []
  (r)
  (if (:inhibited @(:delegator a))
    (unpause a)))

(defn- g [] (or (some-> a :game deref)
                (log/debug "making initial game state")
                (new-game)))

(defn print-tiles
  "Print map, with pred overlayed with X where pred is not true for the tile. If f is supplied print (f tile) for matching tiles, else the glyph."
  ([level]
   (print-tiles (constantly true) level))
  ([pred level]
   (print-tiles pred level nil))
  ([pred level f]
   (doseq [row (:tiles level)]
     (doseq [tile row]
       (print (if (pred tile)
                (if f (f tile) (:glyph tile))
                (if f (:glyph tile) \X))))
     (println))))

(defn print-items
  "Print detailed info about all items on the ground of the current level"
  [game]
  (->> (curlvl game) tile-seq (map :items) (apply concat)
       (map #(vector (type (item-id game %)) % (item-id game %)))
       pprint))

(defn print-inventory
  "Print detailed info about all items in bot's inventory"
  [game]
  (->> game :player :inventory vals
       (map #(vector (type (item-id game %)) % (item-id game %)))
       pprint))

(defn print-los [game]
  (print-tiles (partial visible? game) (curlvl game)))

(defn print-fov [game]
  (print-tiles (partial in-fov? game) (curlvl game)))

(defn print-transparent [game]
  (print-tiles transparent? (curlvl game)))

(defn print-position
  ([game x y] (print-position game (position x y)))
  ([game pos]
   (print-tiles #(= (position %) (position pos)) (curlvl game)
                (constantly \X))))

(defn print-path
  ([game]
   (if-let [path (:last-path game)]
     (print-path game path)))
  ([game path]
   (let [p (into #{} path)]
     (print-tiles (comp p position) (curlvl game) (constantly \X)))))
