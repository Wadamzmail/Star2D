package com.star4droid.star2d.CodeEditor.smart;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.Elements;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Scope;
import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;

/**
 * Completion, parameter hints and error checking on top of the bundled javac.
 *
 * <p>The trick (same idea as CodeAssist): to complete at the caret the file is parsed, then the
 * bodies of every method that doesn't contain the caret are blanked out (offsets stay the same),
 * so javac only has to attribute the few lines that matter. javac then knows the exact type of
 * the expression before the dot, including generics, inheritance and chains.
 */
public final class JavaAnalyzer {
    public static boolean DEBUG;
    private final JavaEnv env;
    private final Map<String, Integer> picks = new HashMap<>();

    // cache of the last unfiltered candidate list (typing more letters only re-filters it)
    private String cacheKey;
    private List<Item> cacheItems;

    public JavaAnalyzer(JavaEnv env) {
        this.env = env;
    }

    public JavaEnv env() {
        return env;
    }

    /** Remember what the user picked, to rank it higher next time. */
    public synchronized void recordPick(String name) {
        Integer c = picks.get(name);
        picks.put(name, c == null ? 1 : Math.min(c + 1, 9));
    }

    // ====================================================================================== errors

    /** Checks one file; {@code others} are extra explicit compilation units (path -> text). */
    public synchronized List<BuildError> diagnose(String path, String text, Map<String, String> others) {
        List<BuildError> out = new ArrayList<>();
        DiagnosticCollector<JavaFileObject> dc = new DiagnosticCollector<>();
        try {
            Prepared p = prepare(path, text, dc);
            p.task.analyze();
        } catch (Throwable t) {
            // a crash inside javac on half-written code: report what we got so far
            if (DEBUG) t.printStackTrace();
        }
        convert(dc, path, text, others, out);
        return out;
    }

    /** A compilation with the whole project entered (so names resolve like in the real build); only {@code mine} gets attributed. */
    private final class Prepared {
        JavacTask task;
        CompilationUnitTree ours;
    }

    private Prepared prepare(String path, String text, DiagnosticCollector<JavaFileObject> dc) throws Exception {
        List<JavaFileObject> units = new ArrayList<>();
        units.add(JavaEnv.source(path, text));
        units.addAll(env.projectUnits(path));
        Prepared p = new Prepared();
        p.task = env.task(units, dc);
        Iterable<? extends CompilationUnitTree> cus = p.task.parse();
        for (CompilationUnitTree cu : cus) {
            String f = cu.getSourceFile() == null ? null : cu.getSourceFile().toUri().getPath();
            if (path.equals(f)) {
                p.ours = cu;
                break;
            }
        }
        return p;
    }

    private void convert(DiagnosticCollector<JavaFileObject> dc, String path, String text, Map<String, String> others, List<BuildError> out) {
        Set<String> seen = new HashSet<>();
        for (Diagnostic<? extends JavaFileObject> d : dc.getDiagnostics()) {
            if (d.getKind() != Diagnostic.Kind.ERROR && d.getKind() != Diagnostic.Kind.WARNING && d.getKind() != Diagnostic.Kind.MANDATORY_WARNING)
                continue;
            if (d.getSource() == null) continue;
            String file = d.getSource().toUri().getPath();
            if (file == null) continue;
            String src = file.equals(path) ? text : (others != null && others.containsKey(file) ? others.get(file) : null);
            BuildError e = new BuildError();
            e.file = file;
            e.line = (int) Math.max(1, d.getLineNumber());
            e.column = (int) Math.max(1, d.getColumnNumber());
            e.warning = d.getKind() != Diagnostic.Kind.ERROR;
            e.message = d.getMessage(null);
            long s = d.getStartPosition(), en = d.getEndPosition();
            if (s < 0) s = d.getPosition();
            e.start = (int) s;
            e.end = (int) en;
            if (src != null && e.start >= 0) {
                if (e.end <= e.start) e.end = Math.max(Lexical.identEnd(src, e.start), e.start + 1);
                if (e.end > src.length()) e.end = src.length();
                // lambdas / method references aren't understood by the (Java 7) parser: don't blame the user
                if (env.lenientLambda) {
                    int ls = Lexical.lineStart(src, Math.min(e.start, src.length()));
                    int le = Lexical.lineEnd(src, Math.min(e.start, src.length()));
                    String line = src.substring(ls, le);
                    if (line.contains("->") || line.contains("::")) continue;
                }
            }
            if (!seen.add(e.file + ":" + e.start + ":" + e.message)) continue;
            out.add(e);
        }
    }

    // ================================================================================ session

    private final class Session {
        JavacTask task;
        Trees trees;
        Elements elements;
        Types types;
        CompilationUnitTree cu;
        SourcePositions sp;
        String text;
    }

    /** Parse, blank the bodies that don't contain {@code keep}, parse again and attribute. */
    /** Reflection: the Trees.instance overload set mentions javax.annotation.processing, which Android doesn't have. */
    static Trees treesOf(JavacTask task) throws Exception {
        return (Trees) Trees.class.getMethod("instance", javax.tools.JavaCompiler.CompilationTask.class).invoke(null, task);
    }

    private Session open(String path, String text, int keep) throws Exception {
        // 1st pass: find method bodies
        DiagnosticCollector<JavaFileObject> dc0 = new DiagnosticCollector<>();
        List<JavaFileObject> units = new ArrayList<>();
        units.add(JavaEnv.source(path, text));
        JavacTask t0 = env.task(units, dc0);
        Iterable<? extends CompilationUnitTree> cus = t0.parse();
        CompilationUnitTree cu0 = cus.iterator().next();
        final SourcePositions sp0 = treesOf(t0).getSourcePositions();
        final List<int[]> ranges = new ArrayList<>();
        final CompilationUnitTree fcu = cu0;
        final int k = keep;
        new TreeScanner<Void, Void>() {
            @Override
            public Void visitMethod(MethodTree m, Void v) {
                BlockTree b = m.getBody();
                if (b != null) {
                    long s = sp0.getStartPosition(fcu, b), e = sp0.getEndPosition(fcu, b);
                    if (s >= 0 && e > s + 1) {
                        if (k >= s && k <= e) return super.visitMethod(m, v);
                        ranges.add(new int[]{(int) s + 1, (int) e - 1});
                        return null;
                    }
                }
                return super.visitMethod(m, v);
            }
        }.scan(cu0, null);
        String work = text;
        if (!ranges.isEmpty()) {
            StringBuilder sb = new StringBuilder(text);
            for (int[] r : ranges) Lexical.blank(sb, r[0], r[1]);
            work = sb.toString();
        }
        // 2nd pass: the whole project entered, only this file attributed
        Session s = new Session();
        s.text = work;
        Prepared pr = prepare(path, work, new DiagnosticCollector<JavaFileObject>());
        s.task = pr.task;
        s.cu = pr.ours;
        try {
            s.task.analyze();
        } catch (Throwable ignored) {
        }
        s.trees = treesOf(s.task);
        s.elements = s.task.getElements();
        s.types = s.task.getTypes();
        s.sp = s.trees.getSourcePositions();
        return s;
    }

    /** Deepest tree that contains {@code off}. */
    private TreePath pathAt(final Session s, final int off) {
        final TreePath[] best = new TreePath[1];
        new TreePathScanner<Void, Void>() {
            @Override
            public Void scan(Tree tree, Void p) {
                if (tree == null) return null;
                long a = s.sp.getStartPosition(s.cu, tree), b = s.sp.getEndPosition(s.cu, tree);
                if (a >= 0 && b >= 0 && a <= off && off <= b) best[0] = new TreePath(getCurrentPath(), tree);
                if (a >= 0 && b >= 0 && (off < a || off > b) && !(tree instanceof CompilationUnitTree)) return null;
                return super.scan(tree, p);
            }
        }.scan(s.cu, null);
        return best[0];
    }

    // ==================================================================================== complete

    /**
     * @param path   absolute path of the file (also decides which file wins over the source path)
     * @param offset caret offset in {@code text}
     */
    public synchronized CompletionResult complete(String path, String text, int offset) {
        CompletionResult res = new CompletionResult();
        if (offset < 0 || offset > text.length()) return res;
        if (Lexical.stateAt(text, offset) != Lexical.CODE) return res;
        int ps = Lexical.identStart(text, offset);
        String prefix = text.substring(ps, offset);
        if (prefix.length() > 0 && Character.isDigit(prefix.charAt(0))) return res;
        res.prefix = prefix;
        int pj = Lexical.prevNonSpace(text, ps);
        char before = pj >= 0 ? text.charAt(pj) : 0;
        boolean afterDot = before == '.' && !(pj > 0 && Character.isDigit(text.charAt(pj - 1)) && !Character.isJavaIdentifierStart(prevIdentChar(text, pj)));
        if (before == '.' && pj > 0 && text.charAt(pj - 1) == '.') return res;
        res.member = afterDot;

        String lineBefore = text.substring(Lexical.lineStart(text, ps), ps).trim();
        if (lineBefore.startsWith("package ")) return res;
        if (lineBefore.startsWith("import ") && !lineBefore.startsWith("import static")) {
            gatherImport(lineBefore.substring(7).trim(), res.items);
            rank(res.items, prefix, true);
            return res;
        }

        String head = text.substring(0, ps), tail = text.substring(offset);
        String key = path + "\u0000" + ps + "\u0000" + head.hashCode() + "\u0000" + tail.hashCode() + "\u0000" + tail.length();
        List<Item> all;
        if (key.equals(cacheKey) && cacheItems != null) {
            all = cacheItems;
        } else {
            all = new ArrayList<>();
            try {
                gather(path, text, ps, afterDot, pj, all);
            } catch (Throwable t) {
                if (DEBUG) t.printStackTrace();
            }
            cacheKey = key;
            cacheItems = all;
        }
        res.items.addAll(all);
        rank(res.items, prefix, afterDot);
        return res;
    }

    private static char prevIdentChar(String t, int dotIdx) {
        int i = dotIdx - 1;
        while (i >= 0 && Character.isDigit(t.charAt(i))) i--;
        return i >= 0 ? t.charAt(i) : ' ';
    }

    /** Build the working copy (placeholder at the caret) and collect candidates. */
    private void gather(String path, String text, int ps, boolean afterDot, int dotIdx, List<Item> out) throws Exception {
        final String PH = "__s2__";
        StringBuilder w = new StringBuilder(text);
        int caret = ps;
        int idEnd = Lexical.identEnd(text, ps);
        if (afterDot) {
            // "v.nor" is not a statement for javac (the select would be dropped): make it a call
            int nx = Lexical.nextNonSpace(text, idEnd);
            boolean call = nx < text.length() && text.charAt(nx) == '(';
            w.replace(ps, idEnd, call ? PH : PH + "()");
        } else if (idEnd == ps) {
            w.insert(ps, PH);
            if (needsSemicolon(text, ps)) w.insert(ps + PH.length(), ';');
        }
        String work = w.toString();
        Session s = open(path, work, caret);
        TreePath tp = pathAt(s, caret);
        boolean statementLike = true;

        if (afterDot) {
            int exprEnd = dotIdx; // index of '.'
            MemberSelectTree ms = null;
            TreePath msPath = null;
            // find the member select whose expression ends right before the dot
            final int fe = exprEnd;
            final Session fs = s;
            final TreePath[] found = new TreePath[1];
            new TreePathScanner<Void, Void>() {
                @Override
                public Void visitMemberSelect(MemberSelectTree node, Void v) {
                    long e = fs.sp.getEndPosition(fs.cu, node.getExpression());
                    if (e == fe) found[0] = getCurrentPath();
                    return super.visitMemberSelect(node, v);
                }
            }.scan(s.cu, null);
            msPath = found[0];
            if (msPath == null) {
                gatherPackageFallback(text, dotIdx, out);
                return;
            }
            ms = (MemberSelectTree) msPath.getLeaf();
            TreePath exprPath = new TreePath(msPath, ms.getExpression());
            gatherMembers(s, exprPath, msPath, out);
            return;
        }

        // plain name: scope
        if (tp == null) {
            tp = classPathAt(s, caret);
            if (tp == null) return;
        }
        boolean inCode = false;
        for (TreePath p = tp; p != null; p = p.getParentPath()) {
            Tree l = p.getLeaf();
            if (l instanceof MethodTree || l instanceof BlockTree) { inCode = true; break; }
            if (l instanceof ClassTree) break;
        }
        gatherScope(s, tp, inCode, out);
    }

    private TreePath classPathAt(final Session s, final int off) {
        final TreePath[] best = new TreePath[1];
        new TreePathScanner<Void, Void>() {
            @Override
            public Void visitClass(ClassTree node, Void v) {
                long a = s.sp.getStartPosition(s.cu, node), b = s.sp.getEndPosition(s.cu, node);
                if (a <= off && off <= b) best[0] = getCurrentPath();
                return super.visitClass(node, v);
            }
        }.scan(s.cu, null);
        return best[0];
    }

    /** Decide if a ';' must follow the placeholder so the statement parses. */
    private static boolean needsSemicolon(String t, int at) {
        int depth = 0;
        for (int i = at - 1; i >= 0; i--) {
            char c = t.charAt(i);
            if (c == ')' || c == ']') depth++;
            else if (c == '(' || c == '[') {
                if (depth == 0) return false; // inside an argument list
                depth--;
            } else if (c == ';' || c == '{' || c == '}') break;
        }
        int le = Lexical.lineEnd(t, at);
        String rest = t.substring(at, le).trim();
        return rest.length() == 0 || rest.charAt(0) == '}';
    }

    // ----------------------------------------------------------------------------- members

    private void gatherMembers(Session s, TreePath exprPath, TreePath msPath, List<Item> out) {
        Scope scope = null;
        try {
            scope = s.trees.getScope(msPath);
        } catch (Throwable ignored) {
        }
        Element el = null;
        try {
            el = s.trees.getElement(exprPath);
        } catch (Throwable ignored) {
        }
        TypeMirror tm = null;
        try {
            tm = s.trees.getTypeMirror(exprPath);
        } catch (Throwable ignored) {
        }

        if (el instanceof PackageElement) {
            addPackageContents(((PackageElement) el).getQualifiedName().toString(), out);
            return;
        }
        if (el instanceof TypeElement && isTypeName(el, exprPath)) {
            TypeElement te = (TypeElement) el;
            addMembersOf(s, scope, te, (DeclaredType) te.asType(), true, out);
            Item c = keyword("class", "class");
            out.add(c);
            if (te.getKind() == ElementKind.ENUM) return;
            return;
        }
        if (tm == null || tm.getKind() == TypeKind.ERROR || tm.getKind() == TypeKind.NONE || tm.getKind() == TypeKind.VOID) {
            // maybe an unresolved package chain
            return;
        }
        for (int guard = 0; guard < 4; guard++) {
            if (tm.getKind() == TypeKind.TYPEVAR) tm = ((TypeVariable) tm).getUpperBound();
            else if (tm.getKind() == TypeKind.WILDCARD) {
                TypeMirror b = ((WildcardType) tm).getExtendsBound();
                tm = b != null ? b : s.elements.getTypeElement("java.lang.Object").asType();
            } else break;
        }
        if (tm.getKind() == TypeKind.ARRAY) {
            Item len = new Item();
            len.kind = Item.Kind.FIELD;
            len.name = len.label = len.insert = "length";
            len.detail = "int";
            out.add(len);
            TypeElement obj = s.elements.getTypeElement("java.lang.Object");
            if (obj != null) addMembersOf(s, scope, obj, (DeclaredType) obj.asType(), false, out);
            return;
        }
        if (tm.getKind() == TypeKind.DECLARED) {
            DeclaredType dt = (DeclaredType) tm;
            Element te = dt.asElement();
            if (te instanceof TypeElement) addMembersOf(s, scope, (TypeElement) te, dt, false, out);
            return;
        }
        if (tm.getKind().isPrimitive()) return;
        {
            try {
                for (TypeMirror sup : s.types.directSupertypes(tm))
                    if (sup instanceof DeclaredType && ((DeclaredType) sup).asElement() instanceof TypeElement)
                        addMembersOf(s, scope, (TypeElement) ((DeclaredType) sup).asElement(), (DeclaredType) sup, false, out);
            } catch (Throwable ignored) {
            }
        }
    }

    /** True when the expression is a bare type name (static access) and not a variable of that type. */
    private boolean isTypeName(Element el, TreePath exprPath) {
        ElementKind k = el.getKind();
        return k == ElementKind.CLASS || k == ElementKind.INTERFACE || k == ElementKind.ENUM || k == ElementKind.ANNOTATION_TYPE;
    }

    private void addMembersOf(Session s, Scope scope, TypeElement te, DeclaredType asType, boolean staticOnly, List<Item> out) {
        List<? extends Element> all;
        try {
            all = s.elements.getAllMembers(te);
        } catch (Throwable t) {
            return;
        }
        Set<String> seenSig = new HashSet<>();
        for (Element m : all) {
            Set<Modifier> mods = m.getModifiers();
            boolean isStatic = mods.contains(Modifier.STATIC);
            ElementKind k = m.getKind();
            if (k == ElementKind.CONSTRUCTOR || k == ElementKind.STATIC_INIT || k == ElementKind.INSTANCE_INIT) continue;
            if (staticOnly && !isStatic) continue;
            String nm = m.getSimpleName().toString();
            if (nm.length() == 0 || nm.indexOf('$') >= 0 || nm.indexOf('<') >= 0 || nm.startsWith("access$") || nm.equals("__s2__")) continue;
            if (scope != null) {
                try {
                    if (!mods.contains(Modifier.PUBLIC) && !mods.contains(Modifier.PROTECTED) && !mods.contains(Modifier.PRIVATE)
                            && !(te.getKind() == ElementKind.INTERFACE) && !samePackage(s, scope, te)) continue;
                    if (!s.trees.isAccessible(scope, m, asType)) continue;
                } catch (Throwable ignored) {
                }
            } else if (mods.contains(Modifier.PRIVATE)) continue;
            Item it = elementItem(s, m, asType);
            if (it == null) continue;
            String sig = it.kind + ":" + it.signature();
            if (!seenSig.add(sig)) continue; // overridden duplicates
            it.isStatic = isStatic;
            Element owner = m.getEnclosingElement();
            if (owner instanceof TypeElement) it.owner = ((TypeElement) owner).getSimpleName().toString();
            out.add(it);
        }
    }

    private boolean samePackage(Session s, Scope scope, TypeElement te) {
        try {
            PackageElement a = s.elements.getPackageOf(te);
            TypeElement c = scope.getEnclosingClass();
            return c != null && s.elements.getPackageOf(c).equals(a);
        } catch (Throwable t) {
            return false;
        }
    }

    private void addPackageContents(String pkg, List<Item> out) {
        ClassIndex ix = env.index();
        for (String sub : ix.subPackagesOf(pkg)) {
            Item it = new Item();
            it.kind = Item.Kind.PACKAGE;
            it.name = it.label = sub;
            it.insert = sub;
            out.add(it);
        }
        for (String c : ix.classesIn(pkg)) {
            Item it = new Item();
            it.kind = Item.Kind.CLASS;
            it.name = it.label = c;
            it.insert = c;
            it.owner = pkg;
            out.add(it);
        }
    }

    private void gatherPackageFallback(String text, int dotIdx, List<Item> out) {
        // "java.util." typed without being resolvable by javac: read the dotted name before the dot
        int e = dotIdx, i = e;
        while (i > 0 && (Lexical.isIdent(text.charAt(i - 1)) || text.charAt(i - 1) == '.')) i--;
        String name = text.substring(i, e);
        if (name.length() > 0 && env.index().isPackage(name)) addPackageContents(name, out);
    }

    // -------------------------------------------------------------------------------- scope

    private void gatherScope(Session s, TreePath tp, boolean inCode, List<Item> out) {
        Scope scope = null;
        try {
            scope = s.trees.getScope(tp);
        } catch (Throwable ignored) {
        }
        Set<Element> seen = Collections.newSetFromMap(new IdentityHashMap<Element, Boolean>());
        Set<String> names = new HashSet<>();

        boolean staticCtx = false;
        TypeElement encl = null;
        if (scope != null) {
            ExecutableElement me = scope.getEnclosingMethod();
            if (me != null && me.getModifiers().contains(Modifier.STATIC)) staticCtx = true;
            encl = scope.getEnclosingClass();
            // locals, parameters, local classes
            for (Scope sc = scope; sc != null; sc = sc.getEnclosingScope()) {
                Iterable<? extends Element> locals;
                try {
                    locals = sc.getLocalElements();
                } catch (Throwable t) {
                    continue;
                }
                for (Element e : locals) {
                    if (!seen.add(e)) continue;
                    ElementKind k = e.getKind();
                    if (k == ElementKind.LOCAL_VARIABLE || k == ElementKind.PARAMETER || k == ElementKind.EXCEPTION_PARAMETER
                            || k.name().equals("RESOURCE_VARIABLE")) {
                        String n = e.getSimpleName().toString();
                        if (n.equals("__s2__") || !names.add("v:" + n)) continue;
                        Item it = new Item();
                        it.kind = k == ElementKind.PARAMETER ? Item.Kind.PARAM : Item.Kind.LOCAL;
                        it.name = it.label = it.insert = n;
                        it.detail = Lexical.simpleTypes(e.asType().toString());
                        out.add(it);
                    }
                }
            }
        }
        // members of the enclosing classes (and what they inherit); in a class body only nested types make sense
        int depth = 0;
        for (TypeElement c = inCode ? encl : null; c != null; c = outerClass(c), depth++) {
            DeclaredType dt = (DeclaredType) c.asType();
            List<Item> mem = new ArrayList<>();
            addMembersOf(s, scope, c, dt, false, mem);
            for (Item it : mem) {
                if (staticCtx && depth == 0 && !it.isStatic && (it.kind == Item.Kind.FIELD || it.kind == Item.Kind.CONSTANT || it.kind == Item.Kind.METHOD)) continue;
                it.base = -depth;
                out.add(it);
            }
        }
        if (inCode) {
            for (String kw : CODE_KEYWORDS) out.add(keyword(kw, kw));
            for (String lit : new String[]{"this", "super", "true", "false", "null"}) {
                if (staticCtx && (lit.equals("this") || lit.equals("super"))) continue;
                out.add(keyword(lit, lit));
            }
            addSnippets(out);
        } else {
            for (String kw : MEMBER_KEYWORDS) out.add(keyword(kw, kw));
            if (encl != null) addOverrides(s, encl, out);
        }
        addTypes(s, out);
    }

    private static TypeElement outerClass(TypeElement c) {
        Element e = c.getEnclosingElement();
        while (e != null && !(e instanceof TypeElement)) {
            if (e instanceof PackageElement) return null;
            e = e.getEnclosingElement();
        }
        return (TypeElement) e;
    }

    private void addTypes(Session s, List<Item> out) {
        ClassIndex ix = env.index();
        Map<String, String> explicit = new HashMap<>();
        Set<String> wild = new HashSet<>();
        wild.add("java.lang");
        String curPkg = "";
        if (s.cu.getPackageName() != null) curPkg = s.cu.getPackageName().toString();
        wild.add(curPkg);
        for (ImportTree imp : s.cu.getImports()) {
            String q = imp.getQualifiedIdentifier().toString();
            if (imp.isStatic()) continue;
            if (q.endsWith(".*")) wild.add(q.substring(0, q.length() - 2));
            else explicit.put(q.substring(q.lastIndexOf('.') + 1), q);
        }
        // classes declared in the file itself
        for (Tree d : s.cu.getTypeDecls()) {
            if (d instanceof ClassTree) {
                Item it = new Item();
                it.kind = Item.Kind.CLASS;
                it.name = it.label = it.insert = ((ClassTree) d).getSimpleName().toString();
                out.add(it);
            }
        }
        for (String simple : ix.simpleNames()) {
            List<String> fq = ix.fqnsOf(simple);
            int n = 0;
            // best candidates first
            List<String> sorted = new ArrayList<>(fq);
            if (sorted.size() > 1) {
                final Map<String, String> ex = explicit;
                final Set<String> wl = wild;
                Collections.sort(sorted, new Comparator<String>() {
                    public int compare(String a, String b) {
                        int ra = rankFq(a, ex, wl), rb = rankFq(b, ex, wl);
                        return rb - ra;
                    }
                });
            }
            for (String f : sorted) {
                if (n++ >= 3) break;
                String pkg = f.substring(0, f.lastIndexOf('.'));
                Item it = new Item();
                it.kind = Item.Kind.CLASS;
                it.name = it.label = it.insert = simple;
                it.owner = pkg;
                boolean visible = f.equals(explicit.get(simple)) || (wild.contains(pkg) && !explicit.containsKey(simple));
                if (!visible) it.importFqn = f;
                it.base = ClassIndex.priority(f) + (visible ? 10 : 0);
                out.add(it);
            }
        }
    }

    private static int rankFq(String f, Map<String, String> explicit, Set<String> wild) {
        String simple = f.substring(f.lastIndexOf('.') + 1);
        String pkg = f.substring(0, f.lastIndexOf('.'));
        int r = ClassIndex.priority(f);
        if (f.equals(explicit.get(simple))) r += 100;
        if (wild.contains(pkg)) r += 50;
        return r;
    }

    // --------------------------------------------------------------------------- elements

    private Item elementItem(Session s, Element e, DeclaredType asType) {
        Item it = new Item();
        String name = e.getSimpleName().toString();
        it.name = name;
        it.deprecated = s.elements.isDeprecated(e);
        switch (e.getKind()) {
            case FIELD:
            case ENUM_CONSTANT: {
                boolean constant = e.getKind() == ElementKind.ENUM_CONSTANT
                        || (e.getModifiers().contains(Modifier.STATIC) && e.getModifiers().contains(Modifier.FINAL));
                it.kind = constant ? Item.Kind.CONSTANT : Item.Kind.FIELD;
                it.label = it.insert = name;
                TypeMirror t = e.asType();
                try {
                    t = s.types.asMemberOf(asType, e);
                } catch (Throwable ignored) {
                }
                it.detail = Lexical.simpleTypes(t.toString());
                return it;
            }
            case METHOD: {
                it.kind = Item.Kind.METHOD;
                ExecutableElement ex = (ExecutableElement) e;
                TypeMirror ret = ex.getReturnType();
                List<? extends TypeMirror> pts = null;
                try {
                    ExecutableType et = (ExecutableType) s.types.asMemberOf(asType, e);
                    ret = et.getReturnType();
                    pts = et.getParameterTypes();
                } catch (Throwable ignored) {
                }
                List<? extends VariableElement> ps = ex.getParameters();
                for (int i = 0; i < ps.size(); i++) {
                    TypeMirror pt = pts != null && i < pts.size() ? pts.get(i) : ps.get(i).asType();
                    it.paramTypes.add(Lexical.simpleTypes(pt.toString()));
                    String pn = ps.get(i).getSimpleName().toString();
                    it.paramNames.add(pn.matches("arg\\d+") ? "" : pn);
                }
                it.varArgs = ex.isVarArgs();
                it.returnType = Lexical.simpleTypes(ret.toString());
                it.detail = it.returnType;
                it.label = it.signature();
                it.insert = ps.isEmpty() ? name + "()" : name + "($0)";
                return it;
            }
            case CLASS:
            case INTERFACE:
            case ENUM:
            case ANNOTATION_TYPE:
                it.kind = e.getKind() == ElementKind.INTERFACE ? Item.Kind.INTERFACE : e.getKind() == ElementKind.ENUM ? Item.Kind.ENUM : Item.Kind.CLASS;
                it.label = it.insert = name;
                return it;
            default:
                return null;
        }
    }

    // ----------------------------------------------------------------------- overrides etc.

    private void addOverrides(Session s, TypeElement encl, List<Item> out) {
        Set<String> declared = new HashSet<>();
        for (ExecutableElement m : ElementFilter.methodsIn(encl.getEnclosedElements()))
            declared.add(m.getSimpleName() + "/" + m.getParameters().size());
        List<? extends Element> all;
        try {
            all = s.elements.getAllMembers(encl);
        } catch (Throwable t) {
            return;
        }
        for (ExecutableElement m : ElementFilter.methodsIn(all)) {
            Set<Modifier> mods = m.getModifiers();
            if (mods.contains(Modifier.PRIVATE) || mods.contains(Modifier.FINAL) || mods.contains(Modifier.STATIC)) continue;
            if (m.getEnclosingElement().equals(encl)) continue;
            if (m.getEnclosingElement() instanceof TypeElement && ((TypeElement) m.getEnclosingElement()).getQualifiedName().contentEquals("java.lang.Object")
                    && !(m.getSimpleName().contentEquals("toString") || m.getSimpleName().contentEquals("equals") || m.getSimpleName().contentEquals("hashCode")))
                continue;
            String key = m.getSimpleName() + "/" + m.getParameters().size();
            if (declared.contains(key)) continue;
            Item it = new Item();
            it.kind = Item.Kind.OVERRIDE;
            it.name = m.getSimpleName().toString();
            StringBuilder sig = new StringBuilder(), call = new StringBuilder();
            List<? extends VariableElement> ps = m.getParameters();
            for (int i = 0; i < ps.size(); i++) {
                String type = Lexical.simpleTypes(ps.get(i).asType().toString());
                String pn = ps.get(i).getSimpleName().toString();
                if (pn.matches("arg\\d+")) pn = defaultParamName(type, i);
                if (i > 0) {
                    sig.append(", ");
                    call.append(", ");
                }
                sig.append(type).append(' ').append(pn);
                call.append(pn);
                it.paramTypes.add(type);
                it.paramNames.add(pn);
            }
            String ret = Lexical.simpleTypes(m.getReturnType().toString());
            String access = mods.contains(Modifier.PROTECTED) ? "protected " : "public ";
            boolean isAbstract = mods.contains(Modifier.ABSTRACT);
            StringBuilder body = new StringBuilder();
            if (ret.equals("void")) {
                if (!isAbstract) body.append("    super.").append(it.name).append('(').append(call).append(");\n    $0");
                else body.append("    $0");
            } else {
                if (!isAbstract) body.append("    return super.").append(it.name).append('(').append(call).append(");$0");
                else body.append("    return ").append(defaultValue(m.getReturnType())).append(";$0");
            }
            it.insert = "@Override\n" + access + ret + " " + it.name + "(" + sig + ") {\n" + body + "\n}";
            it.label = "override " + it.name + "(" + sig + ")";
            it.detail = ret;
            it.owner = ((TypeElement) m.getEnclosingElement()).getSimpleName().toString();
            it.returnType = ret;
            out.add(it);
        }
    }

    private static String defaultParamName(String type, int i) {
        String t = type.replaceAll("<.*>", "").replace("[]", "s");
        if (t.length() == 0) return "arg" + i;
        String n = Character.toLowerCase(t.charAt(0)) + t.substring(1);
        if (n.equals("int") || n.equals("float") || n.equals("long") || n.equals("double") || n.equals("boolean") || n.equals("char") || n.equals("short") || n.equals("byte"))
            n = n.substring(0, 1) + (i == 0 ? "" : String.valueOf(i));
        return n;
    }

    private static String defaultValue(TypeMirror t) {
        switch (t.getKind()) {
            case BOOLEAN: return "false";
            case BYTE: case SHORT: case INT: return "0";
            case LONG: return "0L";
            case FLOAT: return "0f";
            case DOUBLE: return "0d";
            case CHAR: return "'\\0'";
            default: return "null";
        }
    }

    // --------------------------------------------------------------------- keywords etc.

    private static final String[] CODE_KEYWORDS = {"if", "else", "for", "while", "do", "switch", "case", "default", "break", "continue", "return",
            "new", "try", "catch", "finally", "throw", "instanceof", "int", "float", "double", "long", "short", "byte", "char", "boolean", "void",
            "final", "static"};
    private static final String[] MEMBER_KEYWORDS = {"public", "private", "protected", "static", "final", "abstract", "void", "int", "float", "double",
            "long", "short", "byte", "char", "boolean", "class", "interface", "enum", "extends", "implements", "throws", "synchronized", "volatile", "transient"};

    private static Item keyword(String label, String insert) {
        Item it = new Item();
        it.kind = Item.Kind.KEYWORD;
        it.name = label;
        it.label = label;
        it.insert = insert;
        return it;
    }

    private static void snippet(List<Item> out, String name, String desc, String body) {
        Item it = new Item();
        it.kind = Item.Kind.SNIPPET;
        it.name = name;
        it.label = name;
        it.detail = desc;
        it.insert = body;
        out.add(it);
    }

    private static void addSnippets(List<Item> out) {
        snippet(out, "fori", "for (int i...)", "for (int i = 0; i < $0; i++) {\n    \n}");
        snippet(out, "for", "for loop", "for (int i = 0; i < $0; i++) {\n    \n}");
        snippet(out, "foreach", "for each item", "for (Object item : $0) {\n    \n}");
        snippet(out, "while", "while loop", "while ($0) {\n    \n}");
        snippet(out, "if", "if statement", "if ($0) {\n    \n}");
        snippet(out, "ifelse", "if / else", "if ($0) {\n    \n} else {\n    \n}");
        snippet(out, "switch", "switch statement", "switch ($0) {\n    case 0:\n        break;\n    default:\n        break;\n}");
        snippet(out, "try", "try / catch", "try {\n    $0\n} catch (Exception e) {\n    e.printStackTrace();\n}");
        snippet(out, "sout", "System.out.println", "System.out.println($0);");
    }

    // ================================================================================== import

    private void gatherImport(String typed, List<Item> out) {
        // typed = text between "import " and the identifier being typed, e.g. "com.badlogic.gdx."
        String pkg = typed;
        if (pkg.endsWith(".")) pkg = pkg.substring(0, pkg.length() - 1);
        else if (pkg.length() > 0) pkg = pkg.contains(".") ? pkg.substring(0, pkg.lastIndexOf('.')) : "";
        addPackageContents(pkg, out);
    }

    // ===================================================================================== rank

    private void rank(List<Item> items, String prefix, boolean member) {
        List<Item> keep = new ArrayList<>(items.size());
        for (Item it : items) {
            int f = Fuzzy.score(prefix, it.name);
            if (f < 0) continue;
            if (prefix.length() == 0 && !member) {
                // nothing typed yet: don't dump every class / snippet on the user
                if (it.kind == Item.Kind.SNIPPET || it.kind == Item.Kind.CLASS || it.kind == Item.Kind.INTERFACE || it.kind == Item.Kind.ENUM) continue;
            }
            Item c = it;
            int base = it.base; // depth / class priority set during gathering
            int boost = 0;
            switch (it.kind) {
                case LOCAL: case PARAM: boost = 70; break;
                case FIELD: boost = 45; break;
                case CONSTANT: boost = 40; break;
                case METHOD: boost = 38; break;
                case CLASS: case INTERFACE: case ENUM: boost = 20; break;
                case OVERRIDE: boost = 30; break;
                case PACKAGE: boost = 15; break;
                case SNIPPET: boost = 10; break;
                case KEYWORD: boost = 5; break;
                default: break;
            }
            if (it.kind == Item.Kind.METHOD || it.kind == Item.Kind.FIELD || it.kind == Item.Kind.CONSTANT) {
                // members of the closest class first; inherited Object noise last
                boost += base * 3;
                if ("Object".equals(it.owner)) boost -= 25;
            } else if (it.kind == Item.Kind.CLASS) {
                boost += base;
            }
            Integer pk = picks.get(it.name);
            if (pk != null) boost += pk * 4;
            if (it.deprecated) boost -= 40;
            if (it.isStatic && it.kind == Item.Kind.METHOD && false) boost -= 5;
            c.score = f + boost;
            keep.add(c);
        }
        Collections.sort(keep, new Comparator<Item>() {
            public int compare(Item a, Item b) {
                if (a.score != b.score) return b.score - a.score;
                if (a.label.length() != b.label.length()) return a.label.length() - b.label.length();
                return a.label.compareTo(b.label);
            }
        });
        items.clear();
        int max = Math.min(keep.size(), 150);
        for (int i = 0; i < max; i++) items.add(keep.get(i));
    }

    // ==================================================================================== hints

    /** Parameter hint for the call the caret is inside, or null. */
    public synchronized Hint hint(String path, String text, int offset) {
        if (Lexical.stateAt(text, offset) != Lexical.CODE) return null;
        // find the unclosed '(' and count commas
        int depth = 0, commas = 0, open = -1;
        for (int i = offset - 1; i >= 0; i--) {
            char c = text.charAt(i);
            if (c == ')' || c == ']' || c == '}') depth++;
            else if (c == '(' || c == '[' || c == '{') {
                if (depth == 0) {
                    if (c != '(') return null;
                    open = i;
                    break;
                }
                depth--;
            } else if (c == ',' && depth == 0) commas++;
            else if (c == ';' && depth == 0) return null;
        }
        if (open < 0) return null;
        int nameEnd = Lexical.prevNonSpace(text, open) + 1;
        int nameStart = Lexical.identStart(text, nameEnd);
        if (nameEnd <= nameStart) return null;
        String name = text.substring(nameStart, nameEnd);
        if (name.equals("if") || name.equals("for") || name.equals("while") || name.equals("switch") || name.equals("catch") || name.equals("synchronized"))
            return null;
        int pb = Lexical.prevNonSpace(text, nameStart);
        boolean isNew = false;
        int typeStart = nameStart;
        // "new Foo(" or "new a.b.Foo("
        {
            int k = nameStart;
            while (true) {
                int p = Lexical.prevNonSpace(text, k);
                if (p >= 0 && text.charAt(p) == '.') {
                    int q = Lexical.prevNonSpace(text, p);
                    if (q >= 0 && Lexical.isIdent(text.charAt(q))) {
                        k = Lexical.identStart(text, q + 1);
                        continue;
                    }
                }
                break;
            }
            int p = Lexical.prevNonSpace(text, k);
            if (p >= 2 && text.startsWith("new", p - 2) && (p - 3 < 0 || !Lexical.isIdent(text.charAt(p - 3)))) {
                isNew = true;
                typeStart = k;
            }
        }
        Hint h = new Hint();
        h.name = name;
        h.active = commas;
        try {
            if (isNew) {
                collectConstructors(path, text, nameStart, name, h);
            } else {
                // complete at the method name; keep only methods with that name
                String work = text.substring(0, nameStart) + "__s2__;" + text.substring(Math.min(text.length(), Lexical.lineEnd(text, offset)));
                boolean dot = pb >= 0 && text.charAt(pb) == '.';
                List<Item> all = new ArrayList<>();
                // temporarily use the same gatherer on the shortened source
                gather(path, work, nameStart, dot, pb, all);
                for (Item it : all) if (it.kind == Item.Kind.METHOD && it.name.equals(name)) h.signatures.add(it);
            }
        } catch (Throwable t) {
            return null;
        }
        if (h.signatures.isEmpty()) return null;
        Collections.sort(h.signatures, new Comparator<Item>() {
            public int compare(Item a, Item b) {
                return a.paramTypes.size() - b.paramTypes.size();
            }
        });
        return h;
    }

    private void collectConstructors(String path, String text, int nameStart, String simple, Hint h) throws Exception {
        String work = text.substring(0, nameStart) + "Object o__s2__;" + text.substring(Math.min(text.length(), Lexical.lineEnd(text, nameStart)));
        Session s = open(path, work, nameStart);
        TypeElement te = null;
        TreePath tp = pathAt(s, nameStart);
        Scope scope = null;
        if (tp != null) {
            try {
                scope = s.trees.getScope(tp);
            } catch (Throwable ignored) {
            }
        }
        // same package / imports
        for (Tree d : s.cu.getImports().isEmpty() ? new ArrayList<Tree>() : new ArrayList<Tree>(s.cu.getImports())) {
            String q = ((ImportTree) d).getQualifiedIdentifier().toString();
            if (q.endsWith("." + simple)) {
                te = s.elements.getTypeElement(q);
                break;
            }
        }
        if (te == null) {
            String pkg = s.cu.getPackageName() == null ? "" : s.cu.getPackageName().toString();
            List<String> candidates = new ArrayList<>();
            candidates.add("java.lang." + simple);
            if (pkg.length() > 0) candidates.add(pkg + "." + simple);
            for (ImportTree imp : s.cu.getImports()) {
                String q = imp.getQualifiedIdentifier().toString();
                if (q.endsWith(".*")) candidates.add(q.substring(0, q.length() - 1) + simple);
            }
            for (String f : env.index().fqnsOf(simple)) candidates.add(f);
            for (String f : candidates) {
                te = s.elements.getTypeElement(f);
                if (te != null) break;
            }
        }
        if (te == null) return;
        for (ExecutableElement c : ElementFilter.constructorsIn(te.getEnclosedElements())) {
            if (c.getModifiers().contains(Modifier.PRIVATE)) continue;
            Item it = new Item();
            it.kind = Item.Kind.CONSTRUCTOR;
            it.name = simple;
            for (VariableElement p : c.getParameters()) {
                it.paramTypes.add(Lexical.simpleTypes(p.asType().toString()));
                String pn = p.getSimpleName().toString();
                it.paramNames.add(pn.matches("arg\\d+") ? "" : pn);
            }
            it.varArgs = c.isVarArgs();
            it.label = it.signature();
            h.signatures.add(it);
        }
    }
}
