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

(defn run-menubot [anbf]
  (let [config @(:config anbf)
        login (:nao-login config)
        pass (:nao-pass config)
        logged-in (reify RedrawHandler
                    (redraw [this frame]
                      (when (menu-drawn? frame)
                        (if-not (logged-in? frame)
                          (throw (IllegalStateException. "Failed to login"))
                          (do
                            (deregister-handler anbf this)
                            (log/debug "NAO menubot finished")
                            (raw-write anbf "p") ; play!
                            (started @(:delegator anbf)))))))
        pass-prompt (reify RedrawHandler
                      (redraw [this frame]
                        (when (pass-prompt? frame)
                          (deregister-handler anbf this)
                          ; set up the final handler
                          (register-handler anbf logged-in))))
        start-handler (reify RedrawHandler
                (redraw [this frame]
                  (when (menu-drawn? frame)
                    (log/debug "logging in")
                    (deregister-handler anbf this)
                    ; set up the followup handler
                    (register-handler anbf pass-prompt)
                    (raw-write anbf (login-sequence login pass)))))]
    (register-handler anbf start-handler))
  (log/debug "Waiting for NAO menu to draw"))
