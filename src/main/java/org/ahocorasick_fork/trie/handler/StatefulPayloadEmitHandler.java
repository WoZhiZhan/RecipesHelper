package org.ahocorasick_fork.trie.handler;

import java.util.List;

import org.ahocorasick_fork.trie.PayloadEmit;

public interface StatefulPayloadEmitHandler<T> extends PayloadEmitHandler<T>{
    List<PayloadEmit<T>> getEmits();
}
