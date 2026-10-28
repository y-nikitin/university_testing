package ua.course.store;
public record OrderRequest(String reference, long productId, int quantity, boolean premium, boolean couponValid) {
    public OrderRequest(String reference, long productId, int quantity, boolean premium) {
        this(reference, productId, quantity, premium, false);
    }
}
