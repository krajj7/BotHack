; Navigation for nethack.alt.org dgamelaunch menu - to log in and start the game.

(ns anbf.bot.nao-menu
  (:require [anbf.util :refer :all]
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

(defn start [{:keys [config] :as anbf}]
  (let [logged-in (reify RedrawHandler
                    (redraw [this frame]
                      (when (menu-drawn? frame)
                        (deregister-handler anbf this)
                        (if-not (logged-in? frame)
                          (throw (IllegalStateException. "Failed to login"))
                          (raw-write anbf "p")) ; play!
                        (log/info "NAO menubot finished"))))
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
                      (raw-write anbf (login-sequence (:nao-login config)
                                                      (:nao-pass config))))))]
    (register-handler anbf trigger))
  (log/info "Waiting for NAO menu to draw"))
