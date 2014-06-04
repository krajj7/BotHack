(ns anbf.anbf
  (:require [clojure.tools.logging :as log]
            [anbf.util :refer :all]
            [anbf.jta :refer :all]
            [anbf.action :refer :all]
            [anbf.delegator :refer :all]
            [anbf.dungeon :refer :all]
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

(defn- load-config [fname]
  (try
    (binding [*read-eval* false]
      (read-string (slurp fname)))
    (catch Exception e
      (throw (IllegalStateException.
               (format "Failed to load configuration from %s: %s" fname
                       (.getMessage e)))))))

(defn- start-clj-bot
  "Dynamically loads the given namespace of a bot and runs its init function"
  [anbf bot-ns]
  (require bot-ns)
  (if-let [bot-init-fn (ns-resolve bot-ns 'init)]
    (bot-init-fn anbf)
    (throw (ClassNotFoundException. (str "Failed to resolve init in bot "
                                         bot-ns)))))

(defn- start-java-bot
  "Loads a Java bot class and runs its constructor that accepts anbf.bot.IANBF as the only parameter."
  [anbf bot-class]
  (.newInstance (. (resolve bot-class)
                   (getConstructor (into-array [anbf.bot.IANBF])))
                (into-array [anbf])))

(defn- start-bot [{:keys [config] :as anbf}]
  (if-let [bot (config-get config :bot nil)]
    (start-clj-bot anbf (symbol bot))
    (if-let [javabot (config-get config :javabot nil)]
      (start-java-bot anbf (symbol javabot))
      (throw (IllegalStateException.
               "Missing :bot or :javabot in configuration.")))))

(defn- start-menubot
  "The menubot is responsible for starting the game and letting the delegator know about it by calling 'started' on it when done.  If there is no menubot configured, the game is presumed to be started directly."
  [anbf]
  (when-let [menubot-ns (config-get (:config anbf) :menubot nil)]
    (start-clj-bot anbf (symbol menubot-ns))
    true))

(defn- actions-handler [anbf]
  (let [action-handlers (atom #{})]
    (reify ActionChosenHandler
      (action-chosen [_ action]
        (dorun (map (partial deregister-handler anbf)
                    @action-handlers))
        (reset! action-handlers #{})
        ; TODO user handlers - XXX dorun!
        (when-let [h (handler action anbf)]
          (register-handler anbf priority-top h)
          (swap! action-handlers conj h))))))

(defn new-anbf
  ([] (new-anbf "config/shell-config.edn"))
  ([fname]
   (let [delegator (agent (new-delegator nil) :error-handler #(log/error %2))
         config (load-config fname)
         jta (init-jta config delegator)
         scraper-fn (ref nil)
         game (atom (new-game))
         anbf (ANBF. config delegator jta scraper-fn game)
         scraper (scraper-handler scraper-fn delegator)]
     (send delegator set-writer (partial raw-write jta))
     (-> anbf
         (register-handler priority-top (game-handler game delegator))
         (register-handler priority-bottom
                           (reify FullFrameHandler
                             (full-frame [_ _]
                               (log/debug (-> game deref :dungeon curlvl :monsters)) ; XXX
                               (send delegator choose-action @game))))
         (register-handler (actions-handler anbf))
         (register-handler (reify GameStateHandler
                             (ended [_]
                               (log/info "Game ended")
                               (deregister-handler anbf scraper))
                             (started [_]
                               (log/info "Game started")
                               (register-handler anbf scraper)
                               (start-bot anbf))))
         (register-handler (reify
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
                               (log/info "Connection status: offline"))))))))

(defn start [{:keys [config delegator] :as anbf}]
  (log/info "ANBF instance started")
  (if-not (start-menubot anbf)
    (send delegator started))
  (await delegator)
  (start-jta (:jta anbf)
             (config-get config :host "localhost")
             (config-get config :port 23))
  anbf)

(defn stop [anbf]
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
