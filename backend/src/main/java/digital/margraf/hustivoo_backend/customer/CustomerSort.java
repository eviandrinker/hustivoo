package digital.margraf.hustivoo_backend.customer;

import org.springframework.data.domain.Sort;
public enum CustomerSort {
    NAME_ASC, NAME_DESC, CREATED_ASC, CREATED_DESC;
    public Sort toSort() {
        var direction = this == NAME_DESC || this == CREATED_DESC ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(direction, this == NAME_ASC || this == NAME_DESC ? "displayName" : "createdAt").and(Sort.by("id"));
    }
}
