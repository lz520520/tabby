package tabby.dal.caching.bean.ref;

import com.google.common.hash.Hashing;
import com.google.gson.Gson;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.tagkit.AnnotationTag;
import soot.tagkit.Tag;
import soot.tagkit.VisibilityAnnotationTag;
import soot.tagkit.VisibilityParameterAnnotationTag;
import tabby.dal.caching.bean.edge.Alias;
import tabby.dal.caching.bean.edge.Call;
import tabby.dal.caching.converter.ListInteger2JsonStringConverter;
import tabby.dal.caching.converter.Map2JsonStringConverter;
import tabby.dal.caching.converter.Set2JsonStringConverter;

import javax.persistence.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * @author wh1t3P1g
 * @since 2020/10/9
 */
@Data
@Entity
@Slf4j
@Table(name = "methods")
public class MethodReference {

    @Id
    private String id;

    private String name;
    //    @Column(unique = true)
    @Column(columnDefinition = "TEXT")
    private String signature;

    @Column(columnDefinition = "TEXT")
    private String subSignature;
    private String returnType;
    private int modifiers;
    private String classname;
    private int parameterSize;
    private int methodAnnotationSize;
    private int paramAnnotationSize;
    private String vul;

    @Column(columnDefinition = "TEXT")
    @Convert(converter = Set2JsonStringConverter.class)
    private Set<String> parameters = new HashSet<>();

    @Column(columnDefinition = "TEXT")
    @Convert(converter = Set2JsonStringConverter.class)
    private Set<String> methodAnnotation = new HashSet<>();

    @Column(columnDefinition = "TEXT")
    @Convert(converter = Set2JsonStringConverter.class)
    private Set<String> paramAnnotation = new HashSet<>();


    private boolean isSink = false;
    private boolean isSource = false;
    private boolean isStatic = false;
    private boolean isPolluted = false;
    private boolean hasParameters = false;
    private boolean isInitialed = false;
    private boolean actionInitialed = false;
    private boolean isIgnore = false;
    private boolean isSerializable = false;
    private boolean hasMethodAnnotations = false;
    private boolean hasParamAnnotations = false;
    /**
     * 污染传递点，主要标记2种类型，this和param
     * 其他函数可以依靠relatedPosition，来判断当前位置是否是通路
     * old=new
     * param-0=other value
     *      param-0=param-1,param-0=this.field
     * return=other value
     *      return=param-0,return=this.field
     * this.field=other value
     * 提示经过当前函数调用后，当前函数参数和返回值的relateType会发生如下变化
     */
    @Column(columnDefinition = "TEXT")
    @Convert(converter = Map2JsonStringConverter.class)
    private Map<String, String> actions = new HashMap<>();

    @Convert(converter = ListInteger2JsonStringConverter.class)
    private List<Integer> pollutedPosition = new ArrayList<>();

    @org.springframework.data.annotation.Transient
    private transient Set<Call> callEdge = new HashSet<>();

    /**
     * 父类函数、接口函数的依赖边
     */
    @org.springframework.data.annotation.Transient
    private transient Alias aliasEdge;

    public static MethodReference newInstance(String name, String signature){
        MethodReference methodRef = new MethodReference();
        String id = null;
        if(signature == null || signature.isEmpty()){
            id = Hashing.sha256()
                    .hashString(UUID.randomUUID().toString(), StandardCharsets.UTF_8)
                    .toString();
        }else{
            id = Hashing.sha256() // 相同signature生成的id值也相同
                    .hashString(signature, StandardCharsets.UTF_8)
                    .toString();
        }
        methodRef.setName(name);
        methodRef.setId(id);
        methodRef.setSignature(signature);
        return methodRef;
    }

    public static MethodReference newInstance(String classname, SootMethod method){
        MethodReference methodRef = newInstance(method.getName(), method.getSignature());
        methodRef.setClassname(classname);
        methodRef.setModifiers(method.getModifiers());
        methodRef.setSubSignature(method.getSubSignature());
        methodRef.setStatic(method.isStatic());
        methodRef.setReturnType(method.getReturnType().toString());
        Gson gson = new Gson();
//        VisibilityAnnotationTag visibilityAnnotationTag = null;
//        VisibilityParameterAnnotationTag visibilityParameterAnnotationTag = null;
//        if (method.getTags())

        if(method.getParameterCount() > 0){
            methodRef.setHasParameters(true);
            methodRef.setParameterSize(method.getParameterCount());

            for(int i=0; i<method.getParameterCount();i++){
                List<Object> param = new ArrayList<>();
                param.add(i); // param position
                param.add(method.getParameterType(i).toString()); // param type
                methodRef.getParameters().add(gson.toJson(param));
            }
        }
        // 注解解析
        if(method.getTags().size() > 0){
            for (Tag tag: method.getTags()) {
                if (tag instanceof VisibilityAnnotationTag) {
                    VisibilityAnnotationTag visibilityAnnotationTag = ((VisibilityAnnotationTag) tag);
                    methodRef.setHasMethodAnnotations(true);
                    methodRef.setMethodAnnotationSize(visibilityAnnotationTag.getAnnotations().size());

                    for(int i=0; i< visibilityAnnotationTag.getAnnotations().size();i++){
                        List<Object> annotation = new ArrayList<>();
                        annotation.add(i); // param position
                        annotation.add(visibilityAnnotationTag.getAnnotations().get(i).getType().replace("/", ".")); // param type
                        methodRef.getMethodAnnotation().add(gson.toJson(annotation));
                    }

                } else if (tag instanceof VisibilityParameterAnnotationTag) {
                    VisibilityParameterAnnotationTag visibilityParameterAnnotationTag = ((VisibilityParameterAnnotationTag) tag);
                    methodRef.setHasParamAnnotations(true);
                    methodRef.setParamAnnotationSize(visibilityParameterAnnotationTag.getVisibilityAnnotations().size());

                    for(int i=0; i< visibilityParameterAnnotationTag.getVisibilityAnnotations().size();i++){
                        List<Object> annotation = new ArrayList<>();
                        annotation.add(i); // param position
                        for (AnnotationTag annotationTag: visibilityParameterAnnotationTag.getVisibilityAnnotations().get(i).getAnnotations()) {
                            annotation.add(annotationTag.getType().replace("/", "."));
                        }
                        methodRef.getParamAnnotation().add(gson.toJson(annotation));
                    }

                }
            }
        }
        return methodRef;
    }

    public SootMethod getMethod(){
        SootMethod method = null;
        try{
            SootClass sc = Scene.v().getSootClass(classname);
            if(!sc.isPhantom()){
                method = sc.getMethod(subSignature);
                return method;
            }
        }catch (Exception ignored){

        }
        return null;
    }

    public void addAction(String key, String value){
        actions.put(key, value);
    }

}
