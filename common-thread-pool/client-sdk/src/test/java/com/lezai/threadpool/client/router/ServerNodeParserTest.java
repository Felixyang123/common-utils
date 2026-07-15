package com.lezai.threadpool.client.router;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServerNodeParserTest {

    @Test
    @DisplayName("parse single URL with default weight")
    void parseSingle() {
        var nodes = ServerNodeParser.parse("http://host1:8080");
        assertThat(nodes).hasSize(1);
        assertThat(nodes.get(0).getBaseUrl()).isEqualTo("http://host1:8080");
        assertThat(nodes.get(0).getWeight()).isEqualTo(1);
    }

    @Test
    @DisplayName("parse comma separated URLs with trailing weights")
    void parseWeightedUrls() {
        var nodes = ServerNodeParser.parse("http://host1:8080,http://host2:8080:3,http://host3:8080:2");
        assertThat(nodes).extracting(ServerNode::getBaseUrl)
                .containsExactly("http://host1:8080", "http://host2:8080", "http://host3:8080");
        assertThat(nodes).extracting(ServerNode::getWeight).containsExactly(1, 3, 2);
    }

    @Test
    @DisplayName("invalid URL throws")
    void invalidUrlThrows() {
        assertThatThrownBy(() -> ServerNodeParser.parse("not-a-url"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
