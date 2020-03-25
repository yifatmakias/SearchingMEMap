package com.graph.query;

import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;

public class GreedyQuery {

    private RDFGraph graph;
    private GraphQuery graphQuery;
    private String simFileEdge;
    private String simFileNode;
    private List<List<String>> pathResults;

    public GreedyQuery(RDFGraph graph, GraphQuery graphQuery, String simFileEdge, String simFileNode) {
        this.graph = graph;
        this.graphQuery = graphQuery;
        this.simFileEdge = simFileEdge;
        this.simFileNode = simFileNode;
        this.pathResults = new ArrayList<>();
    }


    public void recursiveRun(int index, String graphNode, int topK) {
        if (index == graphQuery.getEdgesInfo().size())
            return;
        String edgeName = graphQuery.getEdgesInfo().get(index).get(0);
        String firstQueryNode = graphQuery.getEntities().get(index).getName();
        String secondQueryNode = graphQuery.getEntities().get(index+1).getName();

        ReadSimilarityTxtFile read_edge_sim_file = new ReadSimilarityTxtFile(simFileEdge, edgeName);
        Map<String, Double> map_sim_edge = read_edge_sim_file.getMap();
        ReadSimilarityTxtFile read_first_node_sim_file = new ReadSimilarityTxtFile(simFileNode, firstQueryNode);
        Map<String, Double> map_first_sim_node = read_first_node_sim_file.getMap();
        ReadSimilarityTxtFile read_second_node_sim_file = new ReadSimilarityTxtFile(simFileNode, secondQueryNode);
        Map<String, Double> map_second_sim_node = read_second_node_sim_file.getMap();

        // Find the most similar graph node to the given query node.
        if (graphNode == null){
            graphNode = getSimilarGrphNode(firstQueryNode, map_first_sim_node);
        }

        // Find the graph index of the most similar graph node.
        String graphNodeId = "";
        Map<Entity, Integer> entitiesMap= graph.getVexIndex();
        for (Map.Entry<Entity, Integer> entry : entitiesMap.entrySet()) {
            if (entry.getKey().getName().equals(graphNode)) {
                graphNodeId = entry.getKey().getId();
            }
        }

        QueryThreadInfo queryThreadInfo = new QueryThreadInfo(graphNodeId, edgeName, map_sim_edge, map_first_sim_node);
        List<QueryThreadInfo> queryThreadInfos = new LinkedList<>();
        queryThreadInfos.add(queryThreadInfo);
        try {
            AStarQueryNew aStarQueryNew = new AStarQueryNew(graph, queryThreadInfos, "Search", 100, 4);
            aStarQueryNew.run();
            AStarQueryNew.PriorityNode [][] results = aStarQueryNew.taskResults;
            List<String> graphResults = new ArrayList<>();
            for (int i = 0; i < results.length ; i++) {
                for (int j = 0; j < results[i].length ; j++) {
                    if (results[i][j] != null) {
                        for (int pathNode : results[i][j].getPath().getNodes()) {
                            graphResults.add(graph.getNodeData(pathNode).getName());
                        }
                    }
                }
            }
            Map<String, Double> topKMap = getTopKSimilarNodes(map_second_sim_node, graphResults, topK);
            for (int i = 0; i < results.length ; i++) {
                for (int j = 0; j < results[i].length ; j++) {
                    if (results[i][j] != null) {
                        for (int pathNode : results[i][j].getPath().getNodes()) {
                            if (topKMap.keySet().contains(graph.getNodeData(pathNode).getName())) {
                                double rank = (topKMap.get(graph.getNodeData(pathNode).getName()) + results[i][j].getG()) / 2;
                                String startNodeName = graph.getNodeData(results[i][j].getPath().getStart()).getName();
                                List<String> predicates = results[i][j].getPath().getPredicates();
                                String endNodeName = graph.getNodeData(pathNode).getName();
                                List<String> subPath = null;
                                for (List<String> path: pathResults) {
                                    if (path.get(path.size() - 1).equals(startNodeName)) {
                                        subPath = path;
                                        subPath.add(Double.toString(rank));
                                        subPath.addAll(predicates);
                                        subPath.add(endNodeName);
                                        break;
                                    }
                                    else if (path.contains(startNodeName)) {
                                        subPath = new ArrayList<>();
                                        subPath.addAll(path.subList(0, path.indexOf(startNodeName) + 1));
                                        subPath.add(Double.toString(rank));
                                        subPath.addAll(predicates);
                                        subPath.add(endNodeName);
                                        pathResults.add(subPath);
                                        break;
                                    }
                                }
                                if (subPath == null){
                                    subPath = new ArrayList<>();
                                    subPath.add(Double.toString(rank));
                                    subPath.add(startNodeName);
                                    subPath.addAll(predicates);
                                    subPath.add(endNodeName);
                                    pathResults.add(subPath);
                                }
                            }
                        }
                    }
                }
            }
            for (Map.Entry<String, Double> entry : topKMap.entrySet()) {
                recursiveRun(index+1, entry.getKey(), topK);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void printPathResults() {
        Map<Double, List<String>> pathMapResults = getMapPathResults();
        SortedSet<Double> ranks = new TreeSet<>(pathMapResults.keySet());
        DecimalFormat df = new DecimalFormat("#.###");
        int resultCounter = ranks.size();
        for (Double rank : ranks) {
            System.out.println(String.format("***Result Path %s***", resultCounter));
            System.out.print("Path score: " + df.format(rank) + "\nPath: ");
            List<String> path = pathMapResults.get(rank);
            for (int i = 0; i < path.size() - 1; i++) {
                System.out.print(path.get(i) + "->");
            }
            System.out.println(path.get(path.size() -1));
            resultCounter--;
        }
    }

    public List<List<String>> getPathResults() {
        return pathResults;
    }

    private Map<Double, List<String>> getMapPathResults() {
        Map<Double, List<String>> mapResults = new HashMap<>();
        for (List<String> path: pathResults) {
            double finalRank = 0;
            Iterator itr = path.iterator();
            while (itr.hasNext()) {
                String pathItem = (String)itr.next();
                if (isDouble(pathItem)) {
                    finalRank = finalRank + Double.parseDouble(pathItem);
                    itr.remove();
                }
            }
            int normalizationFactor = path.size()/2 + 1;
            finalRank = finalRank / normalizationFactor ;
            mapResults.put(finalRank, path);
        }
        return mapResults;
    }

    private boolean isDouble(String str) {
        try {
            Double.parseDouble(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String getSimilarGrphNode(String queryNode, Map<String, Double> map_sim_node) {
        String graphNode = Collections.max(map_sim_node.entrySet(), Map.Entry.comparingByValue()).getKey();
        return graphNode;
    }

    private Map<String,Double> getTopKSimilarNodes(Map<String, Double> map_sim_node, List<String> graphResults, int k) {
        Map<String, Double> rankedGraphResults = new HashMap<>();
        for (String graphNode: graphResults) {
            rankedGraphResults.put(graphNode, map_sim_node.get(graphNode));
        }
        Map<String,Double> topk =
                rankedGraphResults.entrySet().stream()
                        .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                        .limit(k)
                        .collect(Collectors.toMap(
                                Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
        return topk;
    }
}
