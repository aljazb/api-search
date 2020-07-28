import nltk
from nltk.stem import WordNetLemmatizer
from nltk.stem import PorterStemmer
from sklearn.cluster import KMeans
from sklearn.feature_extraction.text import TfidfVectorizer


lemmatizer = WordNetLemmatizer()
nltk.download('punkt')
porter = PorterStemmer()


def tfidf_vectorizer(documents):
    stem_documents = []
    stem_dictionaries = []

    for doc in documents:
        token_words = nltk.word_tokenize(doc)
        document = [porter.stem(word).lower() for word in token_words]
        stem_dict = {}
        for i, d in enumerate(document):
            stem_dict.setdefault(d, []).append(token_words[i].lower())
        document = ' '.join(document)
        stem_documents.append(document)
        stem_dictionaries.append(stem_dict)

    vectorizer = TfidfVectorizer(stop_words='english')
    return vectorizer.fit_transform(stem_documents), vectorizer.get_feature_names(), stem_dictionaries


def tfidf_kmeans(documents, k):
    tfidf_vector, terms, _ = tfidf_vectorizer(documents)

    model = KMeans(n_clusters=k, init='k-means++', max_iter=100, n_init=1)
    model.fit(tfidf_vector)

    print(model.labels_)

    order_centroids = model.cluster_centers_.argsort()[:, ::-1]
    print(terms)
    for i in range(k):
        print("Cluster %d:" % i),
        for ind in order_centroids[i, :10]:
            print(' %s' % terms[ind])

    return model.labels_
