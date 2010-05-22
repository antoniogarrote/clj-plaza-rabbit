;; @author Antonio Garrote
;; @email antoniogarrote@gmail.com
;; @date 22.05.2010

(ns clj-plaza-rabbit.core
  (:use [clojure.contrib.logging :only [log]])
  (:import [com.rabbitmq.client
            ConnectionParameters
            Connection
            Channel
            Envelope
            AMQP
            ConnectionFactory
            Consumer
            QueueingConsumer]))

(defonce *default-rabbit-parameters* {:username "guest" :password "guest" :host "localhost" :port 5672 :virtual-host "/"})

(defonce *default-rabbit-exchange-parameters* {:type "direct" :durable false :autodelete true})

(defonce *default-rabbit-queue-parameters* {:durable false :exclusive false :autodelete true})

(defn alter-default-rabbit-parameters
  "Alters the default parameters for a new connection"
  ([new-parameters]
     (alter-var-root #'*default-rabbit-parameters* (fn [_] new-parameters))))

(defn check-default-values
  "Adds missing values from the default-rabbit-parameters map"
  ([opts] (merge-with #(if (nil? %2) %1 %2) *default-rabbit-parameters* opts))
  ([opts orig] (merge-with #(if (nil? %2) %1 %2) orig opts)))

(defn connect
  "Connects to a RabbitMQ server.
   Args: :username :password :host :port :virtual-host"
  ([& args]
     (let [{:keys [username password virtual-host port host]} (check-default-values (apply array-map args))
           #^ConnectionParameters params (doto (new ConnectionParameters)
                                           (.setUsername username)
                                           (.setPassword password)
                                           (.setVirtualHost virtual-host)
                                           (.setRequestedHeartbeat 0))
           #^ConnectionFactory f (new ConnectionFactory params)
           #^Connection conn (.newConnection f host (int port))]
       (with-meta {:connection conn :channels (ref {})} {:rabbit true}))))

(defn make-channel
  "Creates a new channel through a rabbit connection with a defined name"
  ([rabbit name]
     (let [chn (.createChannel (:connection rabbit))]
       (dosync (alter (:channels rabbit) (fn [old] (assoc old name chn)))))))

(defn disconnect
  "Disconnects from a rabbit server"
  ([rabbit]
     (.close (:connection rabbit))))

;; Exchanges

(defn declare-exchange
  "Declares an exchange through a channel
   Args: :type :durable :autodelete"
  ([rabbit channel name & args]
     (let [chn (get (deref (:channels rabbit)) channel)
           {:keys [type durable autodelete]} (check-default-values (apply array-map args) *default-rabbit-exchange-parameters*)]
       (log :info (str "*** Declaring exchange " name " through channel " channel " with properties type:" type " durable:" durable " autodelete:" autodelete ))
       (.exchangeDeclare chn name type false durable autodelete {}))))

(defn declare-direct-exchange
  "Declares an direct exchange through a channel
   Args: :durable :autodelete"
  ([rabbit channel name & args]
     (let [chn (get (deref (:channels rabbit)) channel)
           {:keys [type durable autodelete]} (assoc (check-default-values (apply array-map args) *default-rabbit-exchange-parameters*) :type "direct")]
       (log :info (str "*** Declaring exchange " name " through channel " channel " with properties type:" type " durable:" durable " autodelete:" autodelete ))
       (.exchangeDeclare chn name type false durable autodelete {}))))

(defn declare-topic-exchange
  "Declares an topic exchange through a channel
   Args: :durable :autodelete"
  ([rabbit channel name & args]
     (let [chn (get (deref (:channels rabbit)) channel)
           {:keys [type durable autodelete]} (assoc (check-default-values (apply array-map args) *default-rabbit-exchange-parameters*) :type "topic")]
       (log :info (str "*** Declaring exchange " name " through channel " channel " with properties type:" type " durable:" durable " autodelete:" autodelete ))
       (.exchangeDeclare chn name type false durable autodelete {}))))

;; Queues

(defn make-queue
  "Declares and binds a new queue
   Args: :exclusive :durable :autodelete"
  ([rabbit channel queue-name exchange-name routing-key & args]
     (let [chn (get (deref (:channels rabbit)) channel)
           {:keys [exclusive durable autodelete]} (check-default-values (apply array-map args) *default-rabbit-queue-parameters*)]
       (log :info (str "*** Declaring queue " queue-name " through channel " channel " and exchange " exchange-name " with properties exclusive:" exclusive " durable:" durable " autodelete:" autodelete ))
       (.queueDeclare chn queue-name false durable exclusive autodelete {})
       (.queueBind chn queue-name exchange-name routing-key))))

;; Consumer

(defn make-consumer
  "Creates a new consumer handling messages delivered to a queue. The provided functionw will be invoked each time a new message is available"
  ([rabbit channel queue f]
     (let [chn (get (deref (:channels rabbit)) channel)
           consumer (proxy [com.rabbitmq.client.DefaultConsumer] [chn]
                      (handleDelivery [#^String consumerTag #^Envelope envelope #^AMQP.BasicProperties properties body]
                                      (let [msg (String. body)
                                            delivery-tag (.getDeliveryTag envelope)]
                                        (log :info (str "*** recived message with tag " delivery-tag " from queue " queue " and channel " channel))
                                        (f msg))))]
       (.basicConsume chn queue true consumer))))

;; Publisher

(defn publish
  "Publish a message through a channel"
  ([rabbit channel exchange routing-key message]
     (log :info (str "*** publishing a message through channel " channel " to exchange " exchange " with routing key " routing-key))
     (.basicPublish (get (deref (:channels rabbit)) channel)
                    exchange
                    routing-key
                    nil
                    (.getBytes message))))
