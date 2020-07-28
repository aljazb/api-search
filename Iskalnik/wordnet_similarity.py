import numpy as np
from nltk.corpus import wordnet, stopwords
import nltk
from sklearn.feature_extraction.text import TfidfVectorizer
from statistics import mean
import redis
import hashlib
import functools
from tqdm import tqdm

from api_doc_transformer import transform_api_to_document, add_importance_and_semantics, semantic_similarity_weight
from database import service_info, endpoint_info
from elasticsearch_client import get_all_endpoints, get_parameters, get_responses


nltk.download('stopwords')
nltk.download('wordnet')
stop_words = set(stopwords.words('english'))
porter = nltk.PorterStemmer()
r = redis.Redis(
    host='localhost',
    port=6379)


@functools.lru_cache(maxsize=None)
def cached_sim(s1, s2):
    return s1.wup_similarity(s2)


def document_similarity(id1, id2, document1, document2):
    if len(document1) == 0 or len(document2) == 0:
        return 0

    key = str(id1) + ',' + str(id2)
    key_hash = hashlib.md5(key.encode()).hexdigest()
    sim = r.get(key_hash)
    if sim is not None:
        return float(sim)

    synset_document1 = [x for x in document1 if x[0]]
    stem_document1 = [x for x in document1 if x[1]]
    synset_document2 = [x for x in document2 if x[0]]
    stem_document2 = [x for x in document2 if x[1]]

    sim_sum1 = 0
    tfidf_sum1 = 0
    max_of_tfidf2 = 0
    for synset, stem, tfidf in document1:
        # get the similarity value of the most similar word in the other document and modify the formula so it
        # also multiplies with tfidf of max in other document (and normalizes with it)
        max_sim_tfidf = 0
        tfidf_of_max = 0
        if synset:
            for d2 in synset_document2:
                wup = cached_sim(synset, d2[0])
                wup_tfidf = wup * d2[2]
                if wup_tfidf > max_sim_tfidf:
                    max_sim_tfidf = wup_tfidf
                    tfidf_of_max = d2[2]
        else:
            for d2 in stem_document2:
                if d2[1] == stem:
                    if d2[2] > max_sim_tfidf:
                        max_sim_tfidf = d2[2]
                        tfidf_of_max = d2[2]

        sim_sum1 += max_sim_tfidf * tfidf
        if tfidf_of_max > max_of_tfidf2:
            max_of_tfidf2 = tfidf_of_max
        tfidf_sum1 += tfidf * tfidf_of_max

    sim_sum2 = 0
    tfidf_sum2 = 0
    max_of_tfidf1 = 0
    for synset, stem, tfidf in document2:
        # get the similarity value of the most similar word in the other document and modify the formula so it
        # also multiplies with tfidf of max in other document (and normalizes with it)
        max_sim_tfidf = 0
        tfidf_of_max = 0
        if synset:
            for d1 in synset_document1:
                wup = cached_sim(synset, d1[0])
                wup_tfidf = wup * d1[2]
                if wup_tfidf > max_sim_tfidf:
                    max_sim_tfidf = wup_tfidf
                    tfidf_of_max = d1[2]
        else:
            for d1 in stem_document1:
                if d1[1] == stem:
                    if d1[2] > max_sim_tfidf:
                        max_sim_tfidf = d1[2]
                        tfidf_of_max = d1[2]

        sim_sum2 += max_sim_tfidf * tfidf
        if tfidf_of_max > max_of_tfidf1:
            max_of_tfidf1 = tfidf_of_max
        tfidf_sum2 += tfidf * tfidf_of_max

    part1 = sim_sum1 / tfidf_sum1 if tfidf_sum1 != 0 else 0
    part2 = sim_sum2 / tfidf_sum2 if tfidf_sum2 != 0 else 0
    ret = (part1 + part2) / 2

    r.set(key_hash, ret)
    return ret


def get_synset_tfidf_docs(documents):
    lemma_synset_map = {}
    lemma_documents = []

    for document in documents:
        lemma_document = []
        # tokenize and remove stop words
        token_words = [w for w in nltk.word_tokenize(document) if w not in stop_words]
        # get noun synset for every word, filter out if not in corpus, take first lemma, put it in map and add it to list
        for word in token_words:
            synsets = wordnet.synsets(word, pos=wordnet.NOUN)
            if len(synsets) > 0:
                lemma = synsets[0].lemmas()[0].name().lower()
                if '-' not in lemma:
                    lemma_synset_map[lemma] = synsets[0]
                    lemma_document.append(lemma)
            if len(synsets) == 0 or '-' in lemma:
                stem = porter.stem(word)
                lemma_document.append(stem)

        lemma_document = ' '.join(lemma_document)
        lemma_documents.append(lemma_document)

    # vectorize and calculate tf idf
    vectorizer = TfidfVectorizer()
    tfidf_matrix = vectorizer.fit_transform(lemma_documents)

    # get tf idf values for lemmas and transform them back to synsets
    feature_names = vectorizer.get_feature_names()
    synsets_tfidfs_docs = []
    for ind in range(len(documents)):
        feature_index = tfidf_matrix[ind, :].nonzero()[1]
        tfidf_scores = zip(feature_index, [tfidf_matrix[ind, x] for x in feature_index])

        synsets_tfidfs = [(lemma_synset_map[feature_names[i]] if feature_names[i] in lemma_synset_map else None,
                           feature_names[i] if feature_names[i] not in lemma_synset_map else None,
                           s) for (i, s) in tfidf_scores]
        synsets_tfidfs_docs.append(synsets_tfidfs)
    return synsets_tfidfs_docs, {}, {}


def get_weighted_synset_tfidf_docs(documents):
    lemma_synset_map = {}
    lemma_documents = []
    weight_documents = []

    lemma_dictionaries = []
    stem_dictionaries = []
    for document in documents:
        lemma_document = []
        document_weights = {}

        lemma_dict = {}
        stem_dict = {}
        # get noun synset for every word, filter out if not in corpus, take first lemma, put it in map and add it to list
        for word_and_weight in document:
            synsets = wordnet.synsets(word_and_weight[0], pos=wordnet.NOUN)
            if len(synsets) > 0:
                lemma = synsets[0].lemmas()[0].name().lower()
                if '-' not in lemma:
                    lemma_synset_map[lemma] = synsets[0]
                    document_weights.setdefault(lemma, []).append(word_and_weight[1])
                    lemma_document.append(lemma)
                    lemma_dict.setdefault(synsets[0], []).append(word_and_weight[0].lower())
            if len(synsets) == 0 or '-' in lemma:
                stem = porter.stem(word_and_weight[0])
                document_weights.setdefault(stem, []).append(word_and_weight[1])
                lemma_document.append(stem)
                stem_dict.setdefault(stem, []).append(word_and_weight[0].lower())
        lemma_document = ' '.join(lemma_document)
        lemma_documents.append(lemma_document)
        weight_documents.append({k: mean(v) for k, v in document_weights.items()})
        lemma_dictionaries.append(lemma_dict)
        stem_dictionaries.append(stem_dict)

    # vectorize and calculate tf idf
    vectorizer = TfidfVectorizer()
    tfidf_matrix = vectorizer.fit_transform(lemma_documents)

    # get tf idf values for lemmas and transform them back to synsets
    feature_names = vectorizer.get_feature_names()
    synsets_tfidfs_docs = []
    for ind in range(len(documents)):
        feature_index = tfidf_matrix[ind, :].nonzero()[1]
        tfidf_scores = zip(feature_index, [tfidf_matrix[ind, x] for x in feature_index])
        synsets_tfidfs = [(lemma_synset_map[feature_names[i]] if feature_names[i] in lemma_synset_map else None,
                           feature_names[i] if feature_names[i] not in lemma_synset_map else None,
                           s * weight_documents[ind][feature_names[i]]) for (i, s) in tfidf_scores]
        synsets_tfidfs_docs.append(synsets_tfidfs)
    return synsets_tfidfs_docs, lemma_dictionaries, stem_dictionaries


def similarity_network(documents, weighted=False, enr_endpoints=None):
    document_count = len(documents)
    if enr_endpoints:
        synsets_tfidfs_docs = [e['transformed_document'] for e in enr_endpoints]
    else:
        synsets_tfidfs_docs, _, _ = get_weighted_synset_tfidf_docs(documents) if weighted else get_synset_tfidf_docs(documents)

    # calculate similarity for every document combination
    similarity_matrix = np.zeros([document_count, document_count])
    pbar = tqdm(total=len(synsets_tfidfs_docs))
    for ind1, document1 in enumerate(synsets_tfidfs_docs):
        pbar.update(1)
        for ind2 in range(ind1):
            sim = document_similarity(list(documents)[ind1], list(documents)[ind2], document1, synsets_tfidfs_docs[ind2])
            similarity_matrix[ind1, ind2] = sim

    return similarity_matrix


def query_to_document(query):
    documents = []
    # tokenize and remove stop words
    token_words = [w for w in nltk.word_tokenize(query) if w not in stop_words]
    # get noun synset for every word, filter out if not in corpus, take first lemma, put it in map and add it to list
    for word in token_words:
        synsets = wordnet.synsets(word, pos=wordnet.NOUN)
        if len(synsets) > 0:
            documents.append((synsets[0], None, 1.))
        else:
            stem = porter.stem(word)
            documents.append((None, stem, 1.))
    return documents


enriched_endpoints = None


def init():
    global enriched_endpoints
    endpoints = get_all_endpoints()
    documents = [transform_api_to_document(endpoint, get_parameters(endpoint["id"]), get_responses(endpoint["id"]))
                 for endpoint in endpoints]
    weighted_synset_tfidf_docs, _, _ = get_weighted_synset_tfidf_docs(documents)
    enriched_endpoints = add_importance_and_semantics(endpoints, documents, weighted_synset_tfidf_docs, service_info(),
                                                  endpoint_info())


def get_enriched_endpoints():
    return enriched_endpoints


def calculate_similarity(query_string, consul_page_rank_weight, metrics_page_rank_weight, metrics_weighted_page_rank_weight, endpoint_requests_weight):
    query = query_to_document(query_string)
    similarities = [document_similarity(query_string, e['endpoint']['id'], query, e['transformed_document']) for e in enriched_endpoints]
    end_sim_imp = [(e, semantic_similarity_weight * similarities[i] +
                    consul_page_rank_weight * e['consul_page_rank'] +
                    metrics_page_rank_weight * e['metrics_page_rank'] +
                    metrics_weighted_page_rank_weight * e['metrics_weighted_page_rank'] +
                    endpoint_requests_weight * e['endpoint_requests']) for i, e in
                   enumerate(enriched_endpoints)]
    end_sim_imp.sort(reverse=True, key=lambda x: x[1])
    return end_sim_imp


init()
