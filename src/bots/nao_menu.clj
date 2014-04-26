; Navigation for nethack.alt.org dgamelaunch menu - to log in and start the game.

(ns bots.nao-menu
  (:require [anbf.anbf :refer :all]
            [anbf.delegator :refer :all]
            [clojure.tools.logging :as log]))

(defn- login-sequence [login pass]
  (format "l%s\n%s\n" login pass))

(defn- menu-drawn? [frame]
  (some #(.contains % "q) Quit") (:lines frame)))

(defn- pass-prompt? [frame]
  (some #(.contains % "Please enter your username.") (:lines frame)))

(defn- logged-in? [frame]
  (some #(.contains % "Logged in as: ") (:lines frame)))

(defn init [{:keys [delegator] :as anbf}]
  (let [logged-in (reify RedrawHandler
                    (redraw [this frame]
                      (when (menu-drawn? frame)
                        (deregister-handler anbf this)
                        (if-not (logged-in? frame)
                          (throw (IllegalStateException. "Failed to login")))
                        (log/info "NAO menubot finished")
                        (send delegator started)
                        (send delegator write "p")))) ; play!
        pass-prompt (reify RedrawHandler
                      (redraw [this frame]
                        (when (pass-prompt? frame)
                          ; set up the final handler
                          (replace-handler anbf this logged-in))))
        trigger (reify RedrawHandler
                  (redraw [this frame]
                    (when (menu-drawn? frame)
                      (log/info "logging in")
                      ; set up the followup handler
                      (replace-handler anbf this pass-prompt)
                      (send delegator write (login-sequence
                                              (config-get anbf :nao-login)
                                              (config-get anbf :nao-pass))))))]
    (register-handler anbf trigger))
  (log/info "Waiting for NAO menu to draw"))
