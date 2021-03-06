package de.halfminer.hms.util;

import java.util.Objects;

public class Pair<L, R> {

    private L left;
    private R right;

    /**
     * Create a new pair
     * @param left left object
     * @param right right object
     */
    public Pair(L left, R right) {
        this.left = left;
        this.right = right;
    }

    /**
     * @return specified object on the left
     */
    public L getLeft() {
        return left;
    }

    /**
     * @return specified object on the left
     */
    public R getRight() {
        return right;
    }

    /**
     * Update value of left node
     * @param setTo value to set
     */
    public void setLeft(L setTo) {
        left = setTo;
    }

    /**
     * Update value of right node
     * @param setTo value to set
     */
    public void setRight(R setTo) {
        right = setTo;
    }

    @Override
    public int hashCode() {
        return Objects.hash(left, right);
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || obj instanceof Pair
                && ((Pair) obj).getLeft().equals(left)
                && ((Pair) obj).getRight().equals(right);
    }
}
