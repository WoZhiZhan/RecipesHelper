package com.wzz.registerhelper.util;

import com.wzz.registerhelper.ModMain;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@SuppressWarnings("all")
public class ResourceUtil {

    private static final MethodHandle FROM_NAMESPACE_AND_PATH;
    private static final MethodHandle PARSE;
    private static final MethodHandle TRY_WITH_RESOURCES;
    private static final MethodHandle VALUE_OF;
    private static final MethodHandle OF;
    private static final Constructor<ResourceLocation> CONSTRUCTOR;

    private static final ResourceLocationFactory FACTORY;

    static {
        ResourceLocationFactory factory1;
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        MethodHandle fromNamespaceAndPath = null;
        MethodHandle parse = null;
        MethodHandle tryWithResources = null;
        MethodHandle valueOf = null;
        MethodHandle of = null;
        Constructor<ResourceLocation> constructor = null;
        // 检测高版本 API
        try {
            fromNamespaceAndPath = lookup.findStatic(ResourceLocation.class, "fromNamespaceAndPath",
                    MethodType.methodType(ResourceLocation.class, String.class, String.class));
            parse = lookup.findStatic(ResourceLocation.class, "parse",
                    MethodType.methodType(ResourceLocation.class, String.class));
            // 如果找到高版本方法，使用高版本工厂
            factory1 = new HighVersionFactory(fromNamespaceAndPath, parse);
        } catch (Exception e) {
            // 高版本方法不存在，尝试低版本方法
            try {
                tryWithResources = lookup.findStatic(ResourceLocation.class, "tryWithResources",
                        MethodType.methodType(ResourceLocation.class, String.class, String.class));
                factory1 = new LowVersionFactory(tryWithResources, LowVersionFactory.MethodType.TRY_WITH_RESOURCES);
            } catch (Exception e1) {
                try {
                    valueOf = lookup.findStatic(ResourceLocation.class, "valueOf",
                            MethodType.methodType(ResourceLocation.class, String.class, String.class));
                    factory1 = new LowVersionFactory(valueOf, LowVersionFactory.MethodType.VALUE_OF);
                } catch (Exception e2) {
                    try {
                        of = lookup.findStatic(ResourceLocation.class, "of",
                                MethodType.methodType(ResourceLocation.class, String.class, String.class));
                        factory1 = new LowVersionFactory(of, LowVersionFactory.MethodType.OF);
                    } catch (Exception e3) {
                        try {
                            constructor = ResourceLocation.class.getDeclaredConstructor(String.class, String.class);
                            constructor.setAccessible(true);
                            factory1 = new LowVersionFactory(constructor, LowVersionFactory.MethodType.CONSTRUCTOR);
                        } catch (Exception e4) {
                            throw new RuntimeException("无法找到可用的 ResourceLocation 创建方法", e4);
                        }
                    }
                }
            }
        }
        FACTORY = factory1;
        FROM_NAMESPACE_AND_PATH = fromNamespaceAndPath;
        PARSE = parse;
        TRY_WITH_RESOURCES = tryWithResources;
        VALUE_OF = valueOf;
        OF = of;
        CONSTRUCTOR = constructor;
    }

    private interface ResourceLocationFactory {
        ResourceLocation create(String namespace, String path);
        ResourceLocation parse(String path);
    }

    private static class HighVersionFactory implements ResourceLocationFactory {
        private final MethodHandle fromNamespaceAndPath;
        private final MethodHandle parse;

        public HighVersionFactory(MethodHandle fromNamespaceAndPath, MethodHandle parse) {
            this.fromNamespaceAndPath = fromNamespaceAndPath;
            this.parse = parse;
        }

        @Override
        public ResourceLocation create(String namespace, String path) {
            try {
                return (ResourceLocation) fromNamespaceAndPath.invokeExact(namespace, path);
            } catch (Throwable e) {
                throw new RuntimeException("高版本创建 ResourceLocation 失败", e);
            }
        }

        @Override
        public ResourceLocation parse(String path) {
            try {
                return (ResourceLocation) parse.invokeExact(path);
            } catch (Throwable e) {
                throw new RuntimeException("高版本解析 ResourceLocation 失败", e);
            }
        }
    }

    private static class LowVersionFactory implements ResourceLocationFactory {
        private final Object method;
        private final MethodType methodType;

        enum MethodType {
            TRY_WITH_RESOURCES, VALUE_OF, OF, CONSTRUCTOR
        }

        public LowVersionFactory(MethodHandle methodHandle, MethodType methodType) {
            this.method = methodHandle;
            this.methodType = methodType;
        }

        public LowVersionFactory(Constructor<ResourceLocation> constructor, MethodType methodType) {
            this.method = constructor;
            this.methodType = methodType;
        }

        @SuppressWarnings("unchecked")
        @Override
        public ResourceLocation create(String namespace, String path) {
            try {
                return switch (methodType) {
                    case TRY_WITH_RESOURCES, VALUE_OF, OF ->
                            (ResourceLocation) ((MethodHandle) method).invokeExact(namespace, path);
                    case CONSTRUCTOR -> ((Constructor<ResourceLocation>) method).newInstance(namespace, path);
                };
            } catch (Throwable e) {
                throw new RuntimeException("低版本创建 ResourceLocation 失败", e);
            }
        }

        @Override
        public ResourceLocation parse(String path) {
            int colonIndex = path.indexOf(':');
            if (colonIndex == -1) {
                return create(ModMain.MODID, path);
            } else {
                String namespace = path.substring(0, colonIndex);
                String localPath = path.substring(colonIndex + 1);
                return create(namespace, localPath);
            }
        }
    }

    /**
     * 创建ResourceLocation的通用方法
     */
    private static ResourceLocation createResourceLocation(String namespace, String path) {
        return FACTORY.create(namespace, path);
    }

    /**
     * 解析ResourceLocation的通用方法
     */
    private static ResourceLocation parseResourceLocation(String path) {
        return FACTORY.parse(path);
    }

    public static ResourceLocation createInstance(String path) {
        return createResourceLocation(ModMain.MODID, path);
    }

    public static ResourceLocation createInstance(String name, String path) {
        return createResourceLocation(name, path);
    }

    public static ResourceLocation createInstanceNoNamespace(String path) {
        return parseResourceLocation(path);
    }

    public static ResourceLocation createMinecraftInstance(String path) {
        return createResourceLocation("minecraft", path);
    }

    public static ResourceLocation createInstanceWithColon(String all) {
        String namespace;
        String path;
        if (all.contains(":")) {
            int colonIndex = all.indexOf(":");
            namespace = all.substring(0, colonIndex);
            path = all.substring(colonIndex + 1);
        } else {
            namespace = ModMain.MODID;
            path = all;
        }
        return createInstance(namespace, path);
    }

    /**
     * 自动解析 namespace:path
     * 若无冒号，自动补 modid
     */
    public static ResourceLocation create(String id) {
        if (id.contains(":")) {
            return ResourceLocation.parse(id);
        }
        return createInstance(ModMain.MODID, id);
    }

    /**
     * 从 jar 内提取资源到临时文件
     * @param resourcePath 类路径，例如 "/assets/forever_love_sword/textures/ui/halo.png"
     * @return 提取后的文件路径（系统临时文件）
     */
    public static String extractResourceToTemp(String resourcePath) {
        try (InputStream in = ResourceUtil.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                System.err.println("资源未找到: " + resourcePath);
                return null;
            }
            String suffix = resourcePath.contains(".")
                    ? resourcePath.substring(resourcePath.lastIndexOf('.'))
                    : ".tmp";
            Path tempFile = Files.createTempFile("gdi_image_", suffix);
            tempFile.toFile().deleteOnExit();
            Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
            return tempFile.toAbsolutePath().toString();
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * 从JAR包内读取资源流
     * @param resourcePath 资源路径，以"/"开头表示从根目录开始
     * @return 资源输入流，如果找不到返回null
     */
    public static InputStream getResourceAsStream(String resourcePath) {
        if (!resourcePath.startsWith("/")) {
            resourcePath = "/" + resourcePath;
        }
        InputStream inputStream = ResourceUtil.class.getResourceAsStream(resourcePath);
        if (inputStream == null) {
            System.err.println("无法找到资源: " + resourcePath);
        }
        return inputStream;
    }

    /**
     * 安全地读取资源，自动关闭流
     * @param resourcePath 资源路径
     * @param consumer 资源处理回调
     */
    public static void withResource(String resourcePath, ThrowingConsumer<InputStream> consumer) {
        try (InputStream in = getResourceAsStream(resourcePath)) {
            if (in != null) {
                consumer.accept(in);
            }
        } catch (Exception e) {
            System.err.println("处理资源时出错: " + resourcePath);
        }
    }

    /**
     * 检查资源是否存在
     */
    public static boolean resourceExists(String resourcePath) {
        try (InputStream in = getResourceAsStream(resourcePath)) {
            return in != null;
        } catch (Exception e) {
            return false;
        }
    }

    @FunctionalInterface
    public interface ThrowingConsumer<T> {
        void accept(T t) throws Exception;
    }
}
