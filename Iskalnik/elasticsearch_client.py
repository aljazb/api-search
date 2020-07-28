from elasticsearch import Elasticsearch

es = Elasticsearch()

endpoints = es.search(index="apisearchendpoint", body={"size": 10000, "query": {"match_all": {}}})
parameters = es.search(index="apisearchparameter", body={"size": 10000, "query": {"match_all": {}}})
responses = es.search(index="apisearchresponse", body={"size": 10000, "query": {"match_all": {}}})


def get_endpoints_from_table(table):
    return sorted([endpoint["_source"] for endpoint in endpoints['hits']['hits'] if
                   table in endpoint["_source"]["tables"] and not endpoint["_source"]["deprecated"]], key=lambda x: x['id'])


def get_services_from_table(table):
    return set([endpoint["_source"]["service"] for endpoint in endpoints['hits']['hits'] if
                       table in endpoint["_source"]["tables"] and not endpoint["_source"]["deprecated"]])


def get_all_endpoints():
    return sorted([endpoint["_source"] for endpoint in endpoints['hits']['hits']], key=lambda x: x['id'])


def get_parameters(endpoint_id):
    return sorted([parameter["_source"] for parameter in parameters['hits']['hits'] if
            parameter["_source"] and parameter["_source"]["endpoint"] == endpoint_id], key=lambda x: x['name'])


def get_all_parameters():
    return sorted([parameter["_source"] for parameter in parameters['hits']['hits'] if parameter["_source"]], key=lambda x: x['name'])


def get_responses(endpoint_id):
    return sorted([response["_source"]["field"] for response in responses['hits']['hits'] if
                   response["_source"]["field"] and response["_source"]["endpoint"] == endpoint_id], key=lambda x: x['name'])


def get_all_responses():
    return sorted([response["_source"]["field"] for response in responses['hits']['hits'] if response["_source"]["field"]], key=lambda x: x['name'])


def search_endpoints(query):
    return es.search(index="new_apisearchendpoint4", params={"size": 5000}, body={"query": {"query_string": {
        "query": query,
        "fields": ["path^10", "controller^6", "summary^6"]
    }}})
