package digital.margraf.hustivoo_backend.servicecatalog;

import org.springframework.data.domain.Sort;

public enum ServiceSort {
    NAME_ASC, NAME_DESC, PRICE_ASC, PRICE_DESC;
    public Sort toSort() {
        return Sort.by(name().endsWith("DESC") ? Sort.Direction.DESC : Sort.Direction.ASC,
                name().startsWith("NAME") ? "name" : "unitPrice").and(Sort.by("id"));
    }
}
