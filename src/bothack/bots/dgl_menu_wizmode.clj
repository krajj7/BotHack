(ns bothack.bots.dgl-menu-wizmode
  "Navigation for nethack.alt.org/acehack.de dgamelaunch menu - to log in and start the game in wizmode."
  (:require [bothack.bothack :refer :all]
            [bothack.util :refer :all]
            [bothack.frame :refer :all]
            [bothack.delegator :refer :all]
            [bothack.handlers :refer :all]
            [clojure.tools.logging :as log]))

(defn- login-sequence [login pass]
  (format "%s\n%s\n" login pass))

(defn- menu-drawn? [frame]
  (and (some (partial re-seq #"q\) (Quit|Back)") (:lines frame))
       (before-cursor? frame "=> ")))

(defn- user-prompt? [frame]
  (and (some #(.contains % "Please enter your username.") (:lines frame))
       (before-cursor? frame "=> ")))

(defn- logged-in? [frame]
  (some #(.contains % "Logged in as ") (:lines frame)))

(defn init [{:keys [delegator config] :as bh}]
  (let [logged-in (reify RedrawHandler
                    (redraw [this frame]
                      (when (menu-drawn? frame)
                        (deregister-handler bh this)
                        (if-not (logged-in? frame)
                          (throw (IllegalStateException. "Failed to login")))
                        (log/info "DGL menubot finished")
                        (send delegator started)
            ; the n is for acehack.de to select NetHack, for nao it does nothing
                        (send delegator write "d")))) ; play!
        pass-prompt (reify RedrawHandler
                      (redraw [this frame]
                        (when (user-prompt? frame)
                          ; set up the final handler
                          (send delegator write (login-sequence
                                                  (config-get config :dgl-login)
                                                  (config-get config :dgl-pass)))
                          (replace-handler bh this logged-in))))
        trigger (reify RedrawHandler
                  (redraw [this frame]
                    (when (menu-drawn? frame)
                      (log/info "logging in")
                      (send delegator write \l)
                      ; set up the followup handler
                      (replace-handler bh this pass-prompt))))]
    (register-handler bh trigger))
  (log/info "Waiting for DGL menu to draw"))
