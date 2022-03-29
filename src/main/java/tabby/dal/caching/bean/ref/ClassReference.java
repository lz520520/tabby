package tabby.dal.caching.bean.ref;

import com.google.common.hash.Hashing;
import lombok.Data;
import org.springframework.data.annotation.Transient;
import soot.SootClass;
import soot.SootField;
import soot.tagkit.AnnotationTag;
import soot.tagkit.Tag;
import soot.tagkit.VisibilityAnnotationTag;
import soot.tagkit.VisibilityParameterAnnotationTag;
import tabby.config.GlobalConfiguration;
import tabby.dal.caching.bean.edge.Extend;
import tabby.dal.caching.bean.edge.Has;
import tabby.dal.caching.bean.edge.Interfaces;
import tabby.dal.caching.converter.List2JsonStringConverter;
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
@Table(name = "classes")
public class ClassReference {

    @Id
    private String id;
    //    @Column(unique = true)
    private String name;
    private String superClass;

    private boolean isPhantom = false;
    private boolean isInterface = false;
    private boolean hasSuperClass = false;
    private boolean hasInterfaces = false;
    private boolean isInitialed = false;
    private boolean isSerializable = false;
    private boolean hasAnnotations = false;

    /**
     * [[name, modifiers, type],...]
     */
    @Column(columnDefinition = "TEXT")
    @Convert(converter = Set2JsonStringConverter.class)
    private Set<String> fields = new HashSet<>();

    @Column(columnDefinition = "TEXT")
    @Convert(converter = List2JsonStringConverter.class)
    private List<String> interfaces = new ArrayList<>();

    @Column(columnDefinition = "TEXT")
    @Convert(converter = Set2JsonStringConverter.class)
    private Set<String> annotations = new HashSet<>();

    // neo4j relationships
    /**
     * 继承边
     */
    @Transient
    private transient Extend extendEdge = null;

    /**
     * 类成员函数 has边
     * Object A has Method B
     */
    @Transient
    private transient List<Has> hasEdge = new ArrayList<>();

    /**
     * 接口继承边
     * 双向，可以从实现类A到接口B，也可以从接口类B到实现类A
     * A -[:INTERFACE]-> B
     * B -[:INTERFACE]-> A
     */
    @Transient
    private transient Set<Interfaces> interfaceEdge = new HashSet<>();

    public static ClassReference newInstance(String name){
        ClassReference classRef = new ClassReference();
        String id = Hashing.sha256() // 相同class生成的id值也相同
                .hashString(name, StandardCharsets.UTF_8)
                .toString();
        classRef.setId(id);
        classRef.setName(name);
        classRef.setInterfaces(new ArrayList<>());
        classRef.setFields(new HashSet<>());
        return classRef;
    }

    public static ClassReference newInstance(SootClass cls){
        ClassReference classRef = newInstance(cls.getName());
        classRef.setInterface(cls.isInterface());

        // 提取类属性信息
        if(cls.getFieldCount() > 0){
            for (SootField field : cls.getFields()) {
                List<String> fieldInfo = new ArrayList<>();
                fieldInfo.add(field.getName());
                fieldInfo.add(field.getModifiers() + "");
                fieldInfo.add(field.getType().toString());
                classRef.getFields().add(GlobalConfiguration.GSON.toJson(fieldInfo));
            }
        }

        // 提取类注解
        if (cls.getTags().size() > 0) {
            for (Tag tag: cls.getTags()) {
                if (tag instanceof VisibilityAnnotationTag) {
                    VisibilityAnnotationTag visibilityAnnotationTag = ((VisibilityAnnotationTag) tag);
                    classRef.setHasAnnotations(true);
//                    classRef.setMethodAnnotationSize(visibilityAnnotationTag.getAnnotations().size());

                    for(int i=0; i< visibilityAnnotationTag.getAnnotations().size();i++){
                        List<Object> annotation = new ArrayList<>();
                        annotation.add(i); // param position
                        annotation.add(visibilityAnnotationTag.getAnnotations().get(i).getType().replace("/", ".")); // param type
                        classRef.getAnnotations().add(GlobalConfiguration.GSON.toJson(annotation));
                    }

                }
            }
        }

        // 提取父类信息
        if(cls.hasSuperclass() && !cls.getSuperclass().getName().equals("java.lang.Object")){
            // 剔除Object类的继承关系，节省继承边数量
            classRef.setHasSuperClass(cls.hasSuperclass());
            classRef.setSuperClass(cls.getSuperclass().getName());
        }
        // 提取接口信息
        if(cls.getInterfaceCount() > 0){
            classRef.setHasInterfaces(true);
            for (SootClass intface : cls.getInterfaces()) {
                classRef.getInterfaces().add(intface.getName());
            }
        }
        return classRef;
    }

}
