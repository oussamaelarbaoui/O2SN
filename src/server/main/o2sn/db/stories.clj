(ns o2sn.db.stories
  (:require [o2sn.db.core :as db]))

(defn all []
  (let [q-str "for s in stories
                  for c in categories
                     for l in locations
                     filter s.category == c._id
                     and s.location == l._id
                     return merge(s, {category : c,location : l}) "]
    (db/query! q-str)))

(defn owner [story-key]
  (let [id (str "stories/" story-key)
        q-str "for v in 1..1 inbound @id own
               return v"]
    (-> (db/query! q-str {:id id})
        first)))

(defn saying-truth [story-key]
  (let [id (str  "stories/" story-key)
        q-str "for v in 1..1 inbound @id truth
        return v"]
    (db/query! q-str {:id id})))

(defn saying-lie [story-key]
  (let [id (str  "stories/" story-key)
        q-str "for v in 1..1 inbound @id lie
        return v"]
    (db/query! q-str {:id id})))

(defn liking [story-id]
  (let [q-str "for u in 1..1 inbound @id liking
              return u"]
    (db/query! q-str {:id story-id})))

(defn disliking [story-id]
  (let [q-str "for u in 1..1 inbound @id disliking
              return u"]
    (db/query! q-str {:id story-id})))

(defn by-user [user-key]
  (let [id (str "users/" user-key)
        q-str "for s in 1..1 outbound @id own
                   for c in categories
                       for l in locations
                           filter s.category == c._id
                           and s.location == l._id
                           return merge(s,{category: c, location: l})"]
    (db/query! q-str {:id id})))

(defn by-key [story-k]
  (let [story-id (str "stories/" story-k)
        q-str "let s = document(@storyid)
                let cat = document(s.category)
                let location = document(s.location)
                let truth = (for t in  1..1 inbound s._id truth
                                            return t)
                let lie = (for li in 1..1 inbound s._id lie
                            return li)
                let owner = first(for o in 1..1 inbound s._id own
                    return o)

                let likes = (for lik in 1..1 inbound s._id liking
                    return lik)

                let dislikes = (for dis in 1..1 inbound s._id disliking
                    return dis)

                return merge(s,
                    {location : location,
                    category : cat,
                    truth : truth,
                    lie : lie,
                    owner : owner,
                    likes : likes,
                    dislikes : dislikes})"]
    (-> (db/query! q-str {:storyid story-id})
        first)))

;; TODO : this query retrieves a lot of unnecessary fields, only relevant fields should be selected
(defn by-channel [{:keys [channel sort-by order offset count]}]
  (let [id (str "channels/" channel)
        default-cover-img "2589143"
        unformated-str
"for c in channels
    filter c._id == @id
    for l in 0..5 outbound c.location contains
        for s in stories
            filter s.location == l._id
            for cat in categories
                filter s.category == cat._id
                let truth_count  = count(for t in  1..1 inbound s._id truth
                            return t)

                let lie_count = count(for li in 1..1 inbound s._id lie
                            return li)

                let likes = (for lik in 1..1 inbound s._id liking
                            return lik)

                let dislikes = (for dis in 1..1 inbound s._id disliking
                               return dis)

                let cover_img = document(concat(\"images/\", first(s.images) or \"%s\"))

                let result = merge(s,
                        {location : l,
                            category : cat,
                            likes : likes,
                            dislikes : dislikes,
                            truth_count,
                            lie_count,
                            cover_img})
                sort result[@sort_by] %s
                limit @offset, @count
                return result"
        q-str (format unformated-str default-cover-img (name order))]
    (db/query! q-str {:id id
                      :offset offset
                      :count count
                      :sort_by (case sort-by
                                 :truth "truth_count"
                                 :lie "lie_count"
                                 :title "title"
                                 :date "date"
                                 "date")})))

(defn liked-by? [story-k user-k]
  (let [story-id (str "stories/" story-k)
        user-id (str "users/" user-k)
        q-str "for l in liking
                 filter l._from == @user_id
                 and l._to == @story_id
                 return l"]
    (db/query! q-str {:user_id user-id :story_id story-id})))

(defn disliked-by? [story-k user-k]
  (let [story-id (str "stories/" story-k)
        user-id (str "users/" user-k)
        q-str "for d in disliking
                 filter d._from == @user_id
                 and d._to == @story_id
                 return d"]
    (db/query! q-str {:user_id user-id :story_id story-id})))

(defn remove-like! [story-k user-k]
  (let [story-id (str "stories/" story-k)
        user-id (str "users/" user-k)
        q-str "for l in liking
                 filter l._from == @user_id
                 and l._to == @story_id
                 remove l in liking
                 return OLD"]
    (when (liked-by? story-k user-k)
      (db/query! q-str {:user_id user-id :story_id story-id}))))

(defn remove-dislike! [story-k user-k]
  (let [story-id (str "stories/" story-k)
        user-id (str "users/" user-k)
        q-str "for l in disliking
                 filter l._from == @user_id
                 and l._to == @story_id
                 remove l in disliking
                 return OLD"]
    (when (disliked-by? story-k user-k)
      (db/query! q-str {:user_id user-id :story_id story-id}))))

(defn add-like! [story-k user-k]
  (let [story-id (str "stories/" story-k)
        user-id (str "users/" user-k)]
    (db/with-coll :liking
      (-> (db/insert-doc! {:_from user-id :_to story-id}
                          {:return-new true}
                          [:new])
          :new
          db/ednize))))

(defn add-dislike! [story-k user-k]
  (let [story-id (str "stories/" story-k)
        user-id (str "users/" user-k)]
    (db/with-coll :disliking
      (-> (db/insert-doc! {:_from user-id :_to story-id}
                          {:return-new true}
                          [:new])
          :new
          db/ednize))))

(defn toggle-like! [story-k user-k]
  (if (liked-by? story-k user-k)
    (do (remove-like! story-k user-k)
        {:liked false})
    (do (add-like! story-k user-k)
        (when (disliked-by? story-k user-k)
          (remove-dislike! story-k user-k))
        {:liked true :disliked false})))

(defn toggle-dislike! [story-k user-k]
  (if (disliked-by? story-k user-k)
    (do (remove-dislike! story-k user-k)
        {:disliked false})
    (do (add-dislike! story-k user-k)
        (when (liked-by? story-k user-k)
          (remove-like! story-k user-k))
        {:disliked true :liked false})))

(defn marked-truth? [story-k user-k]
  (let [story-id (str "stories/" story-k)
        user-id (str "users/" user-k)
        q-str "for t in truth
                 filter t._from == @user_id
                 and t._to == @story_id
                 return t"]
    (db/query! q-str {:user_id user-id :story_id story-id})))

(defn marked-lie? [story-k user-k]
  (let [story-id (str "stories/" story-k)
        user-id (str "users/" user-k)
        q-str "for l in lie
                 filter l._from == @user_id
                 and l._to == @story_id
                 return l"]
    (db/query! q-str {:user_id user-id :story_id story-id})))

(defn unmark-truth! [story-k user-k]
  (let [story-id (str "stories/" story-k)
        user-id (str "users/" user-k)
        q-str "for t in truth
                 filter t._from == @user_id
                 and t._to == @story_id
                 remove t in truth
                 return OLD"]
    (when (marked-truth? story-k user-k)
      (db/query! q-str {:user_id user-id :story_id story-id}))))

(defn unmark-lie! [story-k user-k]
  (let [story-id (str "stories/" story-k)
        user-id (str "users/" user-k)
        q-str "for l in lie
                 filter l._from == @user_id
                 and l._to == @story_id
                 remove l in lie
                 return OLD"]
    (when (marked-lie? story-k user-k)
      (db/query! q-str {:user_id user-id :story_id story-id}))))

(defn mark-truth! [story-k user-k]
  (let [story-id (str "stories/" story-k)
        user-id (str "users/" user-k)]
    (db/with-coll :truth
      (-> (db/insert-doc! {:_from user-id :_to story-id}
                          {:return-new true}
                          [:new])
          :new
          db/ednize))))

(defn mark-lie! [story-k user-k]
  (let [story-id (str "stories/" story-k)
        user-id (str "users/" user-k)]
    (db/with-coll :lie
      (-> (db/insert-doc! {:_from user-id :_to story-id}
                          {:return-new true}
                          [:new])
          :new
          db/ednize))))

(defn toggle-truth! [story-k user-k]
  (if (marked-truth? story-k user-k)
    (do (unmark-truth! story-k user-k)
        {:truth false})
    (do (mark-truth! story-k user-k)
        (when (marked-lie? story-k user-k)
          (unmark-lie! story-k user-k))
        {:truth true :lie false})))

(defn toggle-lie! [story-k user-k]
  (if (marked-lie? story-k user-k)
    (do (unmark-lie! story-k user-k)
        {:lie false})
    (do (mark-lie! story-k user-k)
        (when (marked-truth? story-k user-k)
          (unmark-truth! story-k user-k))
        {:lie true :truth false})))

(defn insert-imgs [imgs]
  (let [q-str "for img in @imgs
                    insert {body: img} in images
                    return NEW"]
    (db/query! q-str {:imgs imgs})))

(defn create-story [story-data]

  (db/with-coll :stories
    (-> (db/insert-doc! story-data
                        {:return-new true}
                        [:new])
        :new
        db/ednize)))


(defn set-owner [story-k user-k]
  (db/with-coll :own
    (db/insert-doc! {:_from (str "users/" user-k)
                     :_to (str "stories/" story-k)})))

#_(defn by-key [story-k]
  (db/with-coll :stories
    (db/get-doc story-k)))

(defn by-id [story-id]
  (by-key (second (clojure.string/split story-id #"/"))))
