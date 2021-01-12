(ns next.cljs.shadow-test
  (:require [clojure.string :as string]
            [clojure.test :refer [deftest is testing]]
            [next.cljs.shadow :refer [create-pages
                                      generate-next-js-app
                                      log-warning]]))

(deftest configure-test
  (testing "raises error if :target is not :next.cljs/next-js-app"
    (let [state {:shadow.build/stage :configure
                 :shadow.build/config {:target :npm-module}}]
      (is (thrown?
           IllegalArgumentException
           (generate-next-js-app state)))))
  (testing "rewrites :target to :npm-module"
    (let [state {:shadow.build/stage :configure
                 :shadow.build/config {:target :next.cljs/next-js-app
                                       :next.cljs/output-dir ".next-cljs"}}
          expected :npm-module
          actual (get-in (generate-next-js-app state)
                         [:shadow.build/config :target])]
      (is (= actual expected))))
  (testing "logs a warning when the config has :output-dir, since it will be overwritten."
    (let [warning (atom nil)]
      (with-redefs [log-warning #(reset! warning %)]
        (let [state {:shadow.build/stage :configure
                     :shadow.build/config {:target :next.cljs/next-js-app
                                           :next.cljs/output-dir ".next-cljs"
                                           :output-dir "something"}}]
          (generate-next-js-app state)
          (is (string/includes? @warning "Found :output-dir"))))))
  (testing "raises error if :next.cljs/output-dir is not present"
    (let [state {:shadow.build/stage :configure
                 :shadow.build/config {:target :next.cljs/next-js-app}}]
      (is (thrown?
           IllegalArgumentException
           (generate-next-js-app state)))))
  (testing "adds :output-dir of <:next.cljs/output-dir>/src/cljs"
    (let [state {:shadow.build/stage :configure
                 :shadow.build/config {:target :next.cljs/next-js-app
                                       :next.cljs/output-dir ".next-cljs"}}
          expected ".next-cljs/src/cljs"
          actual (-> (generate-next-js-app state)
                     (get-in [:shadow.build/config :output-dir]))]
      (is (= actual expected)))))

(deftest create-pages-test
  (testing "Raises an error if a var is missing :next.cljs/export-as"
    (let [vars [{:name 'some-namespace.core/page-1
                 :meta {:next.cljs/page "some/page"}}
                {:name 'some-namespace.core/some-func
                 :meta {:next.cljs/page "some/page"
                        :next.cljs/export-as "getStaticProps"}}
                {:name 'some-namespace.other/page-2
                 :meta {:next.cljs/page "some/other/page"
                        :next.cljs/export-as "default"}}]]
      (is (thrown?
           IllegalArgumentException
           (doall (create-pages vars))))))
  (testing "Returns a file with exports for vars with :next.cljs/page metadata"
    (let [vars [{:name 'some-namespace.core/page-1
                 :meta {:next.cljs/page "some/page"
                        :next.cljs/export-as "default"}}
                {:name 'some-namespace.core/some-func
                 :meta {:next.cljs/page "some/page"
                        :next.cljs/export-as "getStaticProps"}}
                {:name 'some-namespace.other/page-2
                 :meta {:next.cljs/page "some/other/page"
                        :next.cljs/export-as "default"}}]
          expected [{:path "src/pages/some/page.js"
                     :content "export {page_1 as default, some_func as getStaticProps} from '../../cljs/some_namespace.core.js';"}
                    {:path "src/pages/some/other/page.js"
                     :content "export {page_2 as default} from '../../../cljs/some_namespace.other.js';"}]
          actual (create-pages vars)]
      (is (= expected actual)))))
