(ns anbf.anbf
  (:require [clojure.tools.logging :as log]
            [anbf.util :refer :all]
            [anbf.jta :refer :all]
            [anbf.delegator :refer :all]
            [anbf.term :refer :all]
            [anbf.game :refer :all]
            [anbf.scraper :refer :all]))

(defn register-handler
  [anbf & args]
  (send (:delegator anbf) #(apply register % args))
  anbf)

(defn deregister-handler
  [anbf handler]
  (send (:delegator anbf) deregister handler)
  anbf)

(defn replace-handler
  [anbf handler-old handler-new]
  (send (:delegator anbf) switch handler-old handler-new)
  anbf)

(defrecord ANBF [config delegator jta scraper game]
  anbf.bot.IANBF
  (registerHandler [this handler]
    (register-handler this handler))
  (registerHandler [this priority handler]
    (register-handler this priority handler))
  (deregisterHandler [this handler]
    (deregister-handler this handler))
  (replaceHandler [this handler-old handler-new]
    (replace-handler this handler-old handler-new))
  (game [this] @(:game this))
  (player [this] (:player @(:game this)))
  (write [this text] (send (:delegator this) write text) this))

(defmethod print-method ANBF [anbf w]
  (.write w "<ANBF instance>"))

(defn config-get-direct [config key]
  (or (get config key)
      (throw (IllegalStateException.
               (str "Configuration missing key: " key)))))

(defn config-get
  "Get a configuration key or return the default, without a default throw an exception if the key is not present."
  ([anbf key]
   (config-get-direct (:config anbf) key))
  ([anbf key default]
   (get (:config anbf) key default)))

(defn- load-config [fname]
  (try
    (binding [*read-eval* false]
      (read-string (slurp fname)))
    (catch Exception e
      (throw (IllegalStateException.
               (format "Failed to load configuration from %s: %s" fname
                       (.getMessage e)))))))

(defn- init-jta [delegator config]
  (case (config-get-direct config :interface)
    :telnet (new-telnet-jta delegator)
    :shell (new-shell-jta delegator (config-get-direct config :nh-command))
    (throw (IllegalArgumentException. "Invalid interface configuration"))))

(defn- start-bot
  "Dynamically loads the given namespace of a bot and runs its init function"
  [anbf bot-ns]
  (require bot-ns)
  (if-let [bot-init-fn (ns-resolve bot-ns 'init)]
    (bot-init-fn anbf)
    (throw (ClassNotFoundException. (str "Failed to resolve init in bot "
                                         bot-ns)))))

(defn- start-menubot
  "The menubot is responsible for starting the game and letting the delegator know about it by calling 'started' on it when done.  If there is no menubot configured, the game is presumed to be started directly."
  [anbf]
  (if-let [menubot-ns (config-get anbf :menubot nil)]
    (start-bot anbf (symbol menubot-ns))
    (log/info "No menubot configured")))

(defn new-anbf
  ([]
   (new-anbf "anbf-shell-config.clj"))
  ([fname]
   (let [delegator (agent (new-delegator nil) :error-handler #(log/error %2))
         config (load-config fname)
         bot (symbol (config-get-direct config :bot))
         jta (init-jta delegator config)
         scraper-fn (ref nil)
         initial-game (atom (new-game))
         anbf (ANBF. config delegator jta scraper-fn initial-game)
         scraper (scraper-handler scraper-fn delegator)]
     (send delegator set-writer (partial raw-write jta))
     (-> anbf
         (register-handler (game-handler initial-game delegator))
         ; TODO register default do-nothing command handlers with bottom priority
         (register-handler (reify GameStateHandler
                             (ended [_]
                               (log/info "Game ended")
                               (deregister-handler anbf scraper))
                             (started [_]
                               (log/info "Game started")
                               (register-handler anbf scraper)
                               (start-bot anbf bot))))
         (register-handler (reify
                             ConnectionStatusHandler
                             (online [_]
                               (log/info "Connection status: online"))
                             (offline [_]
                               (log/info "Connection status: offline"))
                             BOTLHandler
                             (botl [_ status]
                               (log/info "new botl status: " status))
                             ToplineMessageHandler
                             (message [_ text]
                               (log/info "Topline message: " text))))))))

(defn start [anbf]
  (log/info "ANBF instance started")
  (start-menubot anbf)
  (started @(:delegator anbf))
  (start-jta (:jta anbf)
             (config-get anbf :host "localhost")
             (config-get anbf :port 23))
  anbf)

(defn stop
  [anbf]
  (stop-jta (:jta anbf))
  (dosync (ref-set (:scraper anbf) nil))
  (log/info "ANBF instance stopped")
  anbf)

(defn pause [anbf]
  (send (:delegator anbf) set-inhibition true)
  (log/info "pausing")
  anbf)

(defn unpause [anbf]
  (log/info "unpaused")
  (dosync
    (ref-set (:scraper anbf) nil)
    (-> (:delegator anbf)
        (send set-inhibition false)
        (send write (str esc esc))))
  anbf)
