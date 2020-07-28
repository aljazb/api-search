from wordnet_network import db_info_network, partition_endpoints
from wordnet_similarity import get_enriched_endpoints


enriched_endpoints = get_enriched_endpoints()
consul_page_rank_weight = 0.0
metrics_page_rank_weight = 0.0
metrics_weighted_page_rank_weight = 0.0
endpoint_requests_weight = 0.0


def init_cache():
    partitions_services, partition_words = db_info_network()

    cached_filters = []
    for i in range(12):
        fe = [e for e in enriched_endpoints if e['endpoint']['service'] in partitions_services[i]]
        pe, pw = partition_endpoints(fe)
        cached_filters.append((pe, pw))
    return partitions_services, partition_words, cached_filters


def sort_endpoints(e):
    return consul_page_rank_weight * e['consul_page_rank'] + metrics_page_rank_weight * e['metrics_page_rank'] + metrics_weighted_page_rank_weight * e['metrics_weighted_page_rank'] + endpoint_requests_weight * e['endpoint_requests']


def evaluate_grouping(k, consul, metrics, weighted_metrics, requests):
    groups_output = open("groups_output.txt", "w")
    global consul_page_rank_weight
    global metrics_page_rank_weight
    global metrics_weighted_page_rank_weight
    global endpoint_requests_weight
    consul_page_rank_weight = consul
    metrics_page_rank_weight = metrics
    metrics_weighted_page_rank_weight = weighted_metrics
    endpoint_requests_weight = requests
    found_count = 0
    for ga in groups_answers:
        groups = ga.strip().split(';')[0]
        answers = ga.strip().split(';')[1]
        groups_list = groups.split(',')
        answers_list = answers.split(',')
        partitions_endpoints, _ = cached_filters[int(groups_list[0])]
        count = 1
        for g in groups_list[1:]:
            filtered_endpoints = partitions_endpoints[int(g)]
            sorted_filtered_endpoints = sorted(filtered_endpoints, key=sort_endpoints)
            count += 1
            endpoints_set = set([e['endpoint']['id'] for e in sorted_filtered_endpoints])
            top_endpoints = set([e['endpoint']['id'] for e in sorted_filtered_endpoints][:k])
            if len(set(answers_list).intersection(endpoints_set)) == 0:
                groups_output.write("N {}\n".format(count))
                break
            if len(set(answers_list).intersection(top_endpoints)) > 0:
                found_count += 1
                found = True
                groups_output.write("{}\n".format(count))
                break
            partitions_endpoints, _ = partition_endpoints(filtered_endpoints)
    groups_output.write("\n{}\n".format(found_count))
    groups_output.close()