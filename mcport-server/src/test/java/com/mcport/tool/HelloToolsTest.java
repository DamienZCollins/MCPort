package com.mcport.tool;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HelloToolsTest {

    private final HelloTools helloTools = new HelloTools();

    @Test
    void helloReturnsGreeting() {
        assertThat(helloTools.hello("World")).isEqualTo("Hello, World");
    }
}
