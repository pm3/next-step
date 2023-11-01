package io.aston.worker;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

public class LinkedStream<K, V> {
    static class Item<K, V> {
        K key;
        V value;
        Item<K, V> prev;
        Item<K, V> next;

        public Item(K key, V value) {
            this.key = key;
            this.value = value;
        }
    }

    private Item<K, V> first;
    private Item<K, V> last;

    public void add(K key, V value) {
        synchronized (this) {
            Item<K, V> add = new Item<>(key, value);
            if (first == null) {
                this.first = add;
                this.last = add;
            } else {
                Item<K, V> las0 = this.last;
                las0.next = add;
                add.prev = las0;
                this.last = add;
            }
        }
    }

    public V filterAndSearch(BiFunction<K, V, V> queryFn) {
        synchronized (this) {
            for (Item<K, V> i = this.first; i != null; i = i.next) {
                if (cleanFilterFn(i.key, i.value)) {
                    remove(i);
                    continue;
                }
                if (queryFn != null) {
                    V val0 = queryFn.apply(i.key, i.value);
                    if (val0 != null) {
                        remove(i);
                        return val0;
                    }
                }
            }
            return null;
        }
    }

    public boolean cleanFilterFn(K key, V value) {
        return false;
    }

    private void remove(Item<K, V> remove) {
        if (remove.prev == null) this.first = remove.next;
        else remove.prev.next = remove.next;
        if (remove.next == null) this.last = remove.prev;
        else remove.next.prev = remove.prev;
    }

    public List<V> values() {
        List<V> l = new ArrayList<>();
        for (Item<K, V> i = this.first; i != null; i = i.next) {
            l.add(i.value);
        }
        return l;
    }
}
