; interface for bots

(ns anbf.bot)

(defprotocol IANBF
  (register-handler [this handler] "Register a user handler implementing command/event protocols it is interested in to the delegator" )
  (deregister-handler [this handler] "Deregister the given handler from the delegator" )
  (replace-handler [this handler] "Deregister a handler and register a different user handler" )
  (config-get [this key] "Get value for the configuration key, throw IllegalStateException if the key is not present." )
  (game [this])
  (frame [this])
  (player [this])
  (raw-write [this text]))
