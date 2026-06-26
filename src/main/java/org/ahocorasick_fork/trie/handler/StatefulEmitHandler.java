package org.ahocorasick_fork.trie.handler;

import java.util.List;

import org.ahocorasick_fork.trie.Emit;

public interface StatefulEmitHandler extends EmitHandler {
    List<Emit> getEmits();
}
