from elasticsearch_client import search_endpoints
from wordnet_similarity import calculate_similarity
from statistics import mean
import hashlib

queries_answers_file = open("queries_answers.txt")
queries_answers = queries_answers_file.readlines()


def evaluate(max_k=5, consul=0., metrics=0., weighted_metrics=0., endpoint_requests=0.):
    precisions = [[] for _ in range(max_k)]
    recalls = [[] for _ in range(max_k)]
    elastic_precisions = [[] for _ in range(max_k)]
    elastic_recalls = [[] for _ in range(max_k)]
    for ind, qa in enumerate(queries_answers):
        split_qa = qa.strip().split(',')
        query = split_qa[0]
        answers = split_qa[1:]
        elastic_results = [r['_source'] for r in search_endpoints(query)['hits']['hits']]
        # sim = calculate_similarity(query, consul, metrics, weighted_metrics, endpoint_requests)
        results = [r[0]['endpoint'] for r in calculate_similarity(query, consul, metrics, weighted_metrics, endpoint_requests)]
        for k in range(max_k):
            result_endpoint_ids = [r['id'] for r in results[:(k + 1)]]
            elastic_result_endpoint_ids = [r['id'] for r in elastic_results[:(k + 1)]]
            matching = list(set(answers) & set(result_endpoint_ids))
            elastic_matching = list(set(answers) & set(elastic_result_endpoint_ids))
            p = float(len(answers))
            tp = float(len(matching))
            elastic_tp = float(len(elastic_matching))
            precisions[k].append(tp / (k + 1))
            recalls[k].append(tp / p)
            elastic_precisions[k].append(elastic_tp / (k + 1))
            elastic_recalls[k].append(elastic_tp / p)

        file_ind = "{:03d}".format(ind)
        our_file = open("./our_method_results/" + file_ind + ".csv", "w")
        elastic_file = open("./elastic_results/" + file_ind + ".csv", "w")
        for r in results:
            r_id = r['id']
            id_hash = hashlib.md5(r_id.encode()).hexdigest()
            service_hash = hashlib.md5(r['service'].encode()).hexdigest()
            is_correct = r_id in answers
            our_file.write("{},{},{}\n".format(is_correct, id_hash, service_hash))
        for r in elastic_results:
            r_id = r['id']
            id_hash = hashlib.md5(r_id.encode()).hexdigest()
            service_hash = hashlib.md5(r['service'].encode()).hexdigest()
            is_correct = r_id in answers
            elastic_file.write("{},{},{}\n".format(is_correct, id_hash, service_hash))
        our_file.close()
        elastic_file.close()
    precision = [mean(p) for p in precisions]
    recall = [mean(p) for p in recalls]
    elastic_precision = [mean(p) for p in elastic_precisions]
    elastic_recall = [mean(p) for p in elastic_recalls]

    return precision, elastic_precision, recall, elastic_recall


queries_answers_file.close()
