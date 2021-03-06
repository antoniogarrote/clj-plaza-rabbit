(ns clj-plaza-rabbit.core-test
  (:use [clj-plaza-rabbit core] :reload-all)
  (:use [clojure.test]))

(deftest test-default-rabbit-parameters
  (is (= "guest" (:username *default-rabbit-parameters*))))

(deftest test-check-default-values
  (let [optsp (check-default-values {:password "foo"})]
    (is (= "foo" (:password optsp)))
    (is (= "guest" (:username optsp)))))

(deftest test-connect
  (let [rabbit (connect)]
    (is (instance? com.rabbitmq.client.impl.AMQConnection (:connection rabbit)))
    (disconnect rabbit)))

(deftest test-make-channel
  (let [rabbit (connect)
        chn (make-channel rabbit :test)]
    (is (instance? com.rabbitmq.client.impl.AMQConnection (:connection rabbit)))
    (is (instance? com.rabbitmq.client.impl.ChannelN (:test (deref (:channels rabbit)))))
    (disconnect rabbit)))

(deftest test-declare-exchange
  (let [rabbit (connect)
        chn (make-channel rabbit :test)]
    (declare-exchange rabbit :test "test-exchange")
    (is true)
    (disconnect rabbit)))

(deftest test-make-queue
  (let [rabbit (connect)
        chn (make-channel rabbit :test)]
    (declare-exchange rabbit :test "test-exchange")
    (let [*q*  (make-queue rabbit :test "test-queue" "test-exchange" "foo")]
      (is true))
    (disconnect rabbit)))
