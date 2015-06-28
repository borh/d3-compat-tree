(ns d3-compat-tree.tree
  (:require [fast-zip.core :as fz]
            [plumbing.core :refer [for-map]]
            [schema.core :as s])
  (:import [fast_zip.core ZipperLocation ZipperPath]
           [clojure.lang IFn]))

;; # Genre tree creation
;;
;; Output format follows the d3 tree example: https://github.com/mbostock/d3/wiki/Tree-Layout
;;
;; ```json
;; {
;;  "name": "flare",
;;  "children": [
;;   {
;;    "name": "analytics",
;;    "children": [
;;     {
;;      "name": "cluster",
;;      "children": [
;;       {"name": "AgglomerativeCluster", "size": 3938},
;;       {"name": "CommunityStructure", "size": 3812},
;;       {"name": "MergeEdge", "size": 743}
;;      ]
;;     },
;;     {
;;      "name": "graph",
;;      "children": [
;;       {"name": "BetweennessCentrality", "size": 3534},
;;       {"name": "LinkDistance", "size": 5731}
;;      ]
;;     }
;;    ]
;;   }
;;  ]
;; }
;; ```

(s/defschema D3Tree
  (s/maybe
    {:name           s/Str
     s/Keyword       (s/either s/Num s/Bool)
     (s/optional-key :children) [(s/recursive #'D3Tree)]}))

(s/defn tree-zipper :- ZipperLocation
  [root :- {s/Keyword s/Any}]
  (letfn [(make-node [node children]
            (with-meta (assoc node :children (vec children)) (meta node)))]
    (fz/zipper map? :children make-node root)))

(s/defschema TreeNode
  {s/Keyword (s/either s/Str s/Num s/Bool s/Keyword)
   (s/optional-key :children) {(s/either s/Keyword s/Str) (s/recursive #'TreeNode)}})
(s/defschema IndexedNode
  {(s/either s/Keyword s/Str)
   TreeNode})
(s/defschema IndexedNodeSeq
  [(s/one (s/either s/Keyword s/Str) "s")
   TreeNode])
(s/defschema IndexedTree
  (s/either
   TreeNode
   IndexedNode
   IndexedNodeSeq))

;; FIXME Workaround for vector usage where official fast-zip API expects an ISeq.
(defn vector-down
  "Returns the loc of the leftmost child of the node at this loc,
  or nil if no children"
  [^ZipperLocation loc]
  (when (fz/branch? loc)
    (when-let [cs (fz/children loc)]
      (let [node (.-node loc), ^ZipperPath path (.-path loc)]
        (ZipperLocation.
          (.-ops loc)
          (first cs)
          (fz/ZipperPath.
            []
            (next ^clojure.lang.PersistentVector cs)
            path
            (if path (conj (.-pnodes path) node) [node])
            nil))))))
(defn vector-next
  "Moves to the next loc in the hierarchy, depth-first. When reaching
  the end, returns a distinguished loc detectable via end?. If already
  at the end, stays there."
  [^ZipperLocation loc]
  (let [path (.-path loc)]
    (if (identical? :end path)
      loc
      (or
       (if (fz/branch? loc) (vector-down loc))
       (fz/right loc)
       (loop [p loc]
         (if-let [u (fz/up p)]
           (or (fz/right u) (recur u))
           (ZipperLocation. (.-ops loc) (.-node p) :end)))))))
;; END FIXME

(s/defn root-loc-fz :- ZipperLocation
  "zips all the way up and returns the root node, reflecting any changes.
  Modified from fast-zip.core/root to return location over node."
  [loc :- ZipperLocation]
  (if (identical? :end (.path loc))
    loc
    (let [p (fz/up loc)]
      (if p
        (recur p)
        loc))))
(s/defn find-loc :- (s/maybe ZipperLocation)
  [field-name :- (s/either s/Keyword s/Str)
   root :- ZipperLocation]
  (loop [loc root]
    (if loc
      (if (= field-name (-> loc fz/node :name))
        loc
        (recur (fz/right loc))))))

(s/defn update-keys :- {s/Keyword s/Any}
  [m1 :- {s/Keyword s/Any}
   m2 :- {s/Keyword s/Any}
   key-fns :- IFn]
  (let [ks (set (keys key-fns))]
    (merge m1
           (for-map [k ks
                     :let [v1 (k m1) v2 (k m2)]
                     :when (or v1 v2)]
               k ((k key-fns) v1 v2)))))

(s/defn seq-to-tree :- D3Tree
  "Transforms a vector of hierarchies (just another vector) into a tree data structure suitable for export to JavaScript.

  The underlying algorithm utilizes a custom tree zipper function.
  One downside to using zippers here is that searching for the child nodes is linear, but since the tree is heavily branched, this should not pose a problem even with considerable data.
  TODO: sentence-level features Q/A, conversational, etc?"
  [hierarchies :- [{:genre [s/Str] s/Keyword s/Any}]
   & options :- [{(s/optional-key :merge-fns)   {s/Keyword IFn}
                  (s/optional-key :root-name)   s/Str
                  (s/optional-key :root-values) {s/Keyword s/Any}}]]
  (let [{:keys [merge-fns root-name root-values]
         :or   {merge-fns {:count +}
                root-name "Genres"}} (first options)] ; FIXME better destructuring.
    (fz/root                                                ; Return final tree.
      (reduce                                               ; Reduce over hierarchies.
        (fn [tree m]
          (->> (:genre m)
               (reduce                                      ; Reduce over fields in hierarchy (:a, :b, ...).
                 (fn [loc field]
                   (if-let [found-loc (if-let [child-loc (vector-down loc)] (find-loc field child-loc))]
                     (fz/edit found-loc update-keys m merge-fns #_merge-keys #_merge-fn) ; Node already exists: update counts in node.
                     (vector-down                                  ; Add new node and move loc to it (insert-child is a prepend op).
                       (fz/insert-child loc
                                        (assoc (select-keys m (keys merge-fns))
                                          :name field)))))
                 tree)
               root-loc-fz))                                ; Move to root location.
        (tree-zipper (assoc (or root-values
                                (for-map [[k v] merge-fns]
                                  k (reduce v (map k hierarchies))))
                       :name root-name))
        hierarchies))))

(s/defn seq-to-indexed-tree :- IndexedTree
  "Transforms a vector of hierarchies (just another vector) into a tree data structure suitable for export to JavaScript.

  The underlying algorithm utilizes a custom tree zipper function.
  One downside to using zippers here is that searching for the child nodes is linear, but since the tree is heavily branched, this should not pose a problem even with considerable data.
  TODO: sentence-level features Q/A, conversational, etc?"
  [hierarchies :- [{:genre [s/Str] s/Keyword s/Any}]
   & options :- [{(s/optional-key :merge-fns)   {s/Keyword IFn}
                  (s/optional-key :root-name)   (s/either s/Str s/Keyword)
                  (s/optional-key :root-values) {s/Keyword s/Any}}]]
  (let [{:keys [merge-fns root-name root-values]
         :or   {merge-fns {:count +}
                root-name "Genres"}} (first options)] ; FIXME better destructuring.
    (reduce
     (s/fn [tree :- IndexedNode
            m :- {:genre [s/Str] s/Keyword s/Any}]
       (loop [t tree
              current-path (into [root-name] (subvec (:genre m) 0 1))
              next-path (subvec (:genre m) 1)]
         (let [update-path (drop-last (interleave current-path (repeat :children)))
               updated-tree (update-in t update-path
                                       (s/fn :- TreeNode
                                         [subtree :- (s/maybe (s/either IndexedNode TreeNode))]
                                         (if subtree
                                           (update-keys subtree m merge-fns)
                                           (assoc (select-keys m (keys merge-fns))
                                                  :name (last current-path)))))]
           (if (seq next-path)
             (recur updated-tree
                    (into current-path (subvec next-path 0 1))
                    (subvec next-path 1))
             updated-tree))))

     {root-name (assoc (or root-values
                           (for-map [[k v] merge-fns]
                               k (reduce v (map k hierarchies))))
                       :name (name root-name))}

     hierarchies)))

(s/defn tree-path :- (s/either [s/Str] [s/Keyword])
  [tree-loc :- ZipperLocation]
  (loop [path '()
         loc tree-loc]
    (if loc
      (recur
       (conj path (-> loc fz/node :name))
       (fz/up loc))
      (into [] path))))

(s/defn get-count-in-tree :- s/Num
  [field :- (s/either s/Keyword s/Str)
   loc :- ZipperLocation
   ks :- [s/Str]]
  (loop [node-names ks
         t-loc loc
         count nil]
    (if-not node-names ; synthread?
      (if count count 0)
      (if t-loc
        (if-let [found-loc (find-loc (first node-names) t-loc)]
          (recur (next node-names)
                 (vector-down found-loc)
                 (-> found-loc fz/node field))
          0)
        0))))

(s/defn normalize-tree :- D3Tree
  "Given a genre tree in-tree, returns normalized counts by genre, which are passed in as norm-tree.
  The type of normalized counts returned depend on in-tree, while norm-tree can use a number of counts: document, paragraph and sentence."
  [norm-tree :- D3Tree
   in-tree   :- D3Tree
   & options :- [{(s/optional-key :update-field) s/Keyword
                  (s/optional-key :update-fn)    IFn
                  (s/optional-key :boost-factor) s/Num
                  (s/optional-key :clean-up-fn)  IFn}]]
  (let [{:keys [update-field update-fn boost-factor clean-up-fn]
         :or {update-field :count boost-factor 1000000 update-fn /}} (first options) ; FIXME better destructuring.
        in-tree-zipper (tree-zipper in-tree)
        norm-tree-zipper (tree-zipper norm-tree)]
    (loop [loc in-tree-zipper]
      (if (fz/end? loc)
        (fz/root loc)
        (recur
         (vector-next ; NOTE: get-count-in-tree should always return a positive number.
          (fz/edit loc
                   update-in
                   [update-field]
                   (fn [in-tree-val]
                     (let [norm-tree-val (get-count-in-tree update-field norm-tree-zipper (tree-path loc))
                           new-val (update-fn in-tree-val norm-tree-val (/ 1 boost-factor))]
                       (if clean-up-fn
                         (clean-up-fn new-val)
                         new-val))))))))))
