package com.rits.tests.perspectives;

import com.rits.cloning.Cloner;
import com.rits.perspectives.Perspectives;
import com.rits.tests.perspectives.model.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author kostantinos.kougios
 * <p>
 * 30 Nov 2009
 */
public class TestPerspectives {
    private final Cloner cloner = new Cloner();
    private final Perspectives perspectives = new Perspectives(cloner);

	@Test
    public void testViewAs() {
        // we somehow fetch a product, i.e. from the database
        final Product product = new Product(5, "mouse", "a 3-button mouse");

        // we need to "view" this product as an ordered product.
        final OrderedProduct orderedProduct = perspectives.viewAs(OrderedProduct.class, product);
        orderedProduct.setPrice(new BigDecimal("5.44"));
        orderedProduct.setQty(5);

        // test if all ok
        assertEquals(product.getId(), orderedProduct.getId());
        assertEquals(product.getSku(), orderedProduct.getSku());
        assertEquals(product.getTitle(), orderedProduct.getTitle());
        assertEquals(new BigDecimal("5.44"), orderedProduct.getPrice());
        assertEquals(5, orderedProduct.getQty());
    }

	@Test
    public void testViewAs2ExtendedSubclasses() {
        final Product relatedProduct = new RelatedProduct(8, "X5", "RX5");
        final OrderedProduct op = perspectives.viewAs(OrderedProduct.class, relatedProduct);
        assertEquals(relatedProduct.getId(), op.getId());
        assertEquals(relatedProduct.getSku(), op.getSku());
        assertEquals(relatedProduct.getTitle(), op.getTitle());
    }

	@Test
    public void testViewAsNull() {
        final OrderedProduct op = perspectives.viewAs(OrderedProduct.class, null);
        assertNull(op);
    }

	@Test
    public void testViewCollectionAs() {
        final Product p1 = new Product(1, "mouse1", "a mouse");
        final Product p2 = new Product(2, "mouse2", "an other mouse");
        final Product p3 = new Product(3, "keyboard1", "a keyboard");
        final Products products = new Products(p1, p2, p3);

        // view the products from the orderedproducts perspective
        final OrderedProducts orderedProducts = perspectives.viewCollectionAs(new OrderedProducts(), OrderedProduct.class, products);
        assertEquals(products.size(), orderedProducts.size());
        assertEquals(products.get(0), orderedProducts.get(0));
        assertEquals(products.get(1), orderedProducts.get(1));
        assertEquals(products.get(2), orderedProducts.get(2));
    }

	@Test
    public void testViewNullCollectionAs() {
        final OrderedProducts orderedProducts = perspectives.viewCollectionAs(new OrderedProducts(), OrderedProduct.class, null);
        assertNull(orderedProducts);
    }
}
