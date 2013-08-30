(ns vmfest-test.core
  (:require [pallet.script.lib :as lib]
            [pallet.stevedore :refer [checked-script]]
            [vmfest-test.daemontools :as daemontools])
  (:use [clojure.pprint :only [pprint]]
        
        [pallet.api :only [lift converge group-nodes plan-fn]]
        [pallet.configure :only [compute-service]]
        [pallet.action :only [defaction implement-action]]
        [pallet.actions :only [package package-manager remote-file remote-directory directory]]
        [pallet.core :only [group-spec node-spec server-spec]]
        [pallet.actions :only [exec-script exec-checked-script]]
        [pallet.compute :only [images]]
        
        [pallet.crate.automated-admin-user :only [automated-admin-user]]))

(def vmfest (compute-service "vmfest"))

(use '[pallet.compute.vmfest :only [add-image]])
#_ (add-image vmfest "https://s3.amazonaws.com/vmfest-images/ubuntu-12.04.vdi.gz")

(def base-server
  (server-spec
   :node-spec {:image {:image-id :ubuntu-12.04}}
   :phases {:bootstrap (plan-fn (automated-admin-user)
                                (package-manager :update))
            :configure (plan-fn (package "openjdk-6-jdk"))}))

(def zk-server
  (server-spec
   :extends [base-server]
   :phases {:configure (plan-fn (package "zookeeper")
                                (exec-checked-script
                                 "Start Zookeeper Server"
                                 "/usr/share/zookeeper/bin/zkServer.sh start"))}))



(def nimbus-server
  (server-spec
   :extends [base-server]
   :phases {:configure (plan-fn (package "git")
                                (package "zip")
                                (directory "storm-local")
                                (remote-directory "storm-release"
                                                  :local-file "/Users/jackson/d/binary/storm/storm-0.9.0-wip21.zip"
                                                  :unpack :unzip))}))

(def nimbus-zk-group
  (group-spec "storm-nimbus-zk"
              :extends [nimbus-server zk-server daemontools/server-spec]))

#_ (converge {nimbus-zk-group 0} :compute vmfest)
#_ (converge {nimbus-zk-group 1} :compute vmfest)

#_ (lift [nimbus-zk-group] :compute vmfest)



