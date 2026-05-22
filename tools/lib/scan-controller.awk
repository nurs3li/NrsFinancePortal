# Scan one *Controller.java file. Output: service<TAB>method<TAB>path
# Usage: awk -v svc=finance-service -f scan-controller.awk SomeController.java
BEGIN {
    class_prefix = ""
}
{
    line = $0
    if (!class_done && match(line, /@RequestMapping[[:space:]]*\(/)) {
        p = extract_api_paths(line)
        if (p != "") class_prefix = p
        class_done = 1
    }
    if (match(line, /@GetMapping[[:space:]]*\(/)) {
        emit("GET", line)
    } else if (match(line, /@PostMapping[[:space:]]*\(/)) {
        emit("POST", line)
    } else if (match(line, /@PutMapping[[:space:]]*\(/)) {
        emit("PUT", line)
    } else if (match(line, /@DeleteMapping[[:space:]]*\(/)) {
        emit("DELETE", line)
    } else if (match(line, /@PatchMapping[[:space:]]*\(/)) {
        emit("PATCH", line)
    }
}
function extract_api_paths(s,    path, out, n) {
    out = ""
    n = 0
    while (match(s, /"(\/api[^"]*)"/)) {
        path = substr(s, RSTART + 1, RLENGTH - 2)
        if (n++ > 0) out = out " "
        out = out path
        s = substr(s, RSTART + RLENGTH)
    }
    return out
}
function extract_method_paths(s,    path, out, n) {
    out = ""
    n = 0
    while (match(s, /"(\/[^"]*)"/)) {
        path = substr(s, RSTART + 1, RLENGTH - 2)
        if (n++ > 0) out = out " "
        out = out path
        s = substr(s, RSTART + RLENGTH)
    }
    return out
}
function emit(method, line,    pathSuffix, full) {
    pathSuffix = extract_method_paths(line)
    if (pathSuffix == "") pathSuffix = ""
    split(pathSuffix, parts, / /)
    if (length(parts) == 0 || (length(parts) == 1 && parts[1] == "")) {
        parts[1] = ""
        n = 1
    } else {
        n = length(parts)
    }
    if (class_prefix != "") {
        split(class_prefix, cparts, / /)
        for (ci = 1; ci <= length(cparts); ci++) {
            for (pi = 1; pi <= n; pi++) {
                full = join_path(cparts[ci], parts[pi])
                if (full ~ /^\/api/) print svc "\t" method "\t" full
            }
        }
    } else {
        for (pi = 1; pi <= n; pi++) {
            full = parts[pi]
            if (full ~ /^\/api/) print svc "\t" method "\t" full
        }
    }
}
function join_path(a, b,    p) {
    if (a == "") return b
    if (b == "") return a
    p = a
    sub(/\/+$/, "", p)
    if (b !~ /^\//) b = "/" b
    return p b
}
