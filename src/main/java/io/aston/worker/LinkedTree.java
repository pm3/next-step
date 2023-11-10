package io.aston.worker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

public class LinkedTree<K, V> {

    static class Item<K, V> {
        final K key;
        final V value;
        final int pos;
        Item<K, V> prev;
        Item<K, V> next;
        Item<K, V> friend;

        public Item(K key, V value, int pos) {
            this.key = key;
            this.value = value;
            this.pos = pos;
        }
    }

    private Item<K, V> first;
    private Item<K, V> last;
    private int counter = 0;

    private final Map<K, Item<K, V>> lastMap = new HashMap<>();

    public void add(K key, V value) {
        synchronized (this) {
            Item<K, V> add = new Item<>(key, value, ++counter);
            Item<K, V> lastFriend = lastMap.put(key, add);
            if (lastFriend != null) {
                lastFriend.friend = add;
            } else {
                if (first == null) {
                    this.first = add;
                } else {
                    Item<K, V> las0 = this.last;
                    las0.next = add;
                    add.prev = las0;
                }
                this.last = add;
            }
        }
    }

    public V filterAndSearch(BiFunction<K, V, V> queryFn) {
        synchronized (this) {
            Item<K, V> i = this.first;
            while (i != null) {
                if (cleanFilterFn(i.key, i.value)) {
                    i = remove(i);
                    continue;
                }
                if (queryFn != null) {
                    V val0 = queryFn.apply(i.key, i.value);
                    if (val0 != null) {
                        remove(i);
                        return val0;
                    }
                }
                i = i.next;
            }
            return null;
        }
    }

    public boolean cleanFilterFn(K key, V value) {
        return false;
    }

    private Item<K, V> remove(Item<K, V> remove) {
        Item<K, V> next = remove.next;
        Item<K, V> friend = remove.friend;
        lastMap.remove(remove.key, remove);
        if (remove.prev == null) this.first = remove.next;
        else remove.prev.next = remove.next;
        if (remove.next == null) this.last = remove.prev;
        else remove.next.prev = remove.prev;
        remove.prev = null;
        remove.next = null;
        remove.friend = null;
        if (friend != null) {
            for (Item<K, V> i = next; i != null; i = i.next) {
                if (i.pos > friend.pos) {
                    if (i.prev == null) {
                        i.prev = friend;
                        this.first = friend;
                    } else {
                        friend.prev = i.prev;
                        friend.next = i;
                        i.prev.next = friend;
                        i.prev = friend;
                    }
                    return friend.pos < next.pos ? friend : next;
                }
            }
            this.last.next = friend;
            this.last = friend;
        }
        return next;
    }

    public List<V> values() {
        List<V> values = new ArrayList<>();
        for (Item<K, V> i = this.first; i != null; i = i.next) {
            values.add(i.value);
            if (i.friend != null) {
                for (Item<K, V> i2 = i.friend; i2 != null; i2 = i2.friend) {
                    values.add(i2.value);
                }
            }
        }
        return values;
    }
}
