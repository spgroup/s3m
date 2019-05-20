package com.tinkerpop.rexster.config;

import com.tinkerpop.blueprints.Edge;
import com.tinkerpop.blueprints.Features;
import com.tinkerpop.blueprints.Graph;
import com.tinkerpop.blueprints.GraphQuery;
import com.tinkerpop.blueprints.Vertex;
import org.apache.commons.configuration.Configuration;

public class MockGraphConfiguration implements GraphConfiguration {

    public Graph configureGraphInstance(Configuration properties) throws GraphConfigurationException {
        return new MockGraph();
    }

    public class MockGraph implements Graph {

        @Override
        public Features getFeatures() {
            return new Features();
        }

        public Vertex addVertex(Object o) {
            return null;
        }

        public Vertex getVertex(Object o) {
            return null;
        }

        public void removeVertex(Vertex vertex) {
        }

        public Iterable<Vertex> getVertices() {
            return null;
        }

        @Override
        public Iterable<Vertex> getVertices(String key, Object value) {
            return null;
        }

        public Edge addEdge(Object o, Vertex vertex, Vertex vertex1, String s) {
            return null;
        }

        public Edge getEdge(Object o) {
            return null;
        }

        public void removeEdge(Edge edge) {
        }

        public Iterable<Edge> getEdges() {
            return null;
        }

        @Override
        public Iterable<Edge> getEdges(String key, Object value) {
            return null;
        }

        public void shutdown() {
        }

        @Override
        public GraphQuery query() {
            return null;
        }
    }
}

