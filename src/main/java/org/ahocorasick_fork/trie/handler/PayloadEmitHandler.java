package org.ahocorasick_fork.trie.handler;

import org.ahocorasick_fork.trie.PayloadEmit;

public interface PayloadEmitHandler<T> {
    boolean emit(PayloadEmit<T> emit);
}
