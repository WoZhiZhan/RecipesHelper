package com.wzz.registerhelper.util;

import com.github.promeg.pinyinhelper_fork.Pinyin;

import java.util.*;
import java.util.function.Function;

/**
 * 拼音搜索助手 - 通用的拼音搜索工具类
 * 支持中文、拼音、首字母、mod过滤等多种搜索方式
 * 
 * @param <T> 被搜索的对象类型
 */
public class PinyinSearchHelper<T> {
    
    private final Map<T, PinyinInfo> pinyinCache = new HashMap<>();
    private final Function<T, String> displayNameGetter;
    private final Function<T, String> idGetter;
    
    /**
     * 拼音信息类
     */
    public static class PinyinInfo {
        public final String fullPinyin;    // 完整拼音：zuan shi
        public final String initials;      // 首字母：zs
        public final String nospace;       // 无空格拼音：zuanshi
        
        public PinyinInfo(String fullPinyin, String initials, String nospace) {
            this.fullPinyin = fullPinyin;
            this.initials = initials;
            this.nospace = nospace;
        }
    }
    
    /**
     * 搜索过滤器类
     */
    public static class SearchFilter {
        public final String searchText;
        public final String modFilter; // null表示不过滤mod
        
        public SearchFilter(String searchText, String modFilter) {
            this.searchText = searchText;
            this.modFilter = modFilter;
        }
    }
    
    /**
     * 构造函数
     * @param displayNameGetter 获取显示名称的函数
     * @param idGetter 获取ID的函数（用于mod过滤）
     */
    public PinyinSearchHelper(Function<T, String> displayNameGetter, Function<T, String> idGetter) {
        this.displayNameGetter = displayNameGetter;
        this.idGetter = idGetter;
    }
    
    /**
     * 构建拼音缓存
     */
    public void buildCache(Collection<T> items) {
        pinyinCache.clear();
        for (T item : items) {
            String displayName = displayNameGetter.apply(item);
            PinyinInfo pinyinInfo = convertToPinyinInfo(displayName);
            pinyinCache.put(item, pinyinInfo);
        }
    }
    
    /**
     * 获取缓存的拼音信息
     */
    public PinyinInfo getPinyinInfo(T item) {
        return pinyinCache.get(item);
    }
    
    /**
     * 将字符串转换为拼音信息
     */
    public static PinyinInfo convertToPinyinInfo(String text) {
        if (text == null || text.isEmpty()) {
            return new PinyinInfo("", "", "");
        }
        
        try {
            String fullPinyin = Pinyin.toPinyin(text, " ").toLowerCase();
            StringBuilder initials = new StringBuilder();
            StringBuilder nospace = new StringBuilder();
            
            for (char c : text.toCharArray()) {
                if (Pinyin.isChinese(c)) {
                    String pinyin = Pinyin.toPinyin(c).toLowerCase();
                    if (!pinyin.isEmpty()) {
                        initials.append(pinyin.charAt(0));
                        nospace.append(pinyin);
                    }
                } else {
                    initials.append(Character.toLowerCase(c));
                    nospace.append(Character.toLowerCase(c));
                }
            }
            
            return new PinyinInfo(fullPinyin, initials.toString(), nospace.toString());
            
        } catch (Exception e) {
            return new PinyinInfo(text.toLowerCase(), text.toLowerCase(), text.toLowerCase());
        }
    }
    
    /**
     * 检查字符串是否包含中文字符
     */
    public static boolean containsChinese(String text) {
        if (text == null) return false;
        for (char c : text.toCharArray()) {
            if (Pinyin.isChinese(c)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 解析mod过滤器
     */
    public static SearchFilter parseModFilter(String searchText) {
        if (searchText == null || searchText.trim().isEmpty()) {
            return new SearchFilter("", null);
        }
        
        String trimmed = searchText.trim();
        
        int atIndex = trimmed.indexOf('@');
        if (atIndex != -1) {
            String modPart = trimmed.substring(atIndex + 1);
            String textPart = trimmed.substring(0, atIndex).trim();
            String modName = normalizeModName(modPart.toLowerCase());
            return new SearchFilter(textPart, modName);
        }
        
        return new SearchFilter(trimmed, null);
    }
    
    /**
     * 标准化mod名称，处理常见别名
     */
    private static String normalizeModName(String modName) {
        return switch (modName) {
            case "mc", "minec", "minecraft" -> "minecraft";
            case "forge" -> "forge";
            default -> modName;
        };
    }
    
    /**
     * 检查对象是否匹配搜索条件
     */
    public boolean matches(T item, String searchText) {
        SearchFilter filter = parseModFilter(searchText);
        
        // 检查mod过滤
        if (filter.modFilter != null) {
            String id = idGetter.apply(item);
            if (id == null || !id.toLowerCase().contains(filter.modFilter)) {
                return false;
            }
        }
        
        if (filter.searchText.isEmpty()) {
            return true;
        }
        
        String lowerSearch = filter.searchText.toLowerCase().trim();
        
        // 检查ID匹配
        String id = idGetter.apply(item);
        if (id != null && id.toLowerCase().contains(lowerSearch)) {
            return true;
        }
        
        // 检查显示名称匹配
        String displayName = displayNameGetter.apply(item);
        if (displayName != null && displayName.toLowerCase().contains(lowerSearch)) {
            return true;
        }
        
        // 检查拼音匹配
        PinyinInfo pinyinInfo = pinyinCache.get(item);
        if (pinyinInfo != null) {
            // 完整拼音匹配
            if (pinyinInfo.fullPinyin.contains(lowerSearch)) {
                return true;
            }
            // 首字母匹配
            if (pinyinInfo.initials.contains(lowerSearch)) {
                return true;
            }
            // 无空格拼音匹配
            if (pinyinInfo.nospace.contains(lowerSearch)) {
                return true;
            }
            
            // 多词匹配（支持空格分隔的拼音搜索）
            String[] searchWords = lowerSearch.split("\\s+");
            String[] pinyinWords = pinyinInfo.fullPinyin.split("\\s+");
            
            if (searchWords.length > 1 && pinyinWords.length >= searchWords.length) {
                boolean allMatch = true;
                for (String searchWord : searchWords) {
                    boolean found = false;
                    for (String pinyinWord : pinyinWords) {
                        if (pinyinWord.startsWith(searchWord)) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        allMatch = false;
                        break;
                    }
                }
                if (allMatch) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * 过滤集合中的对象
     */
    public List<T> filter(Collection<T> items, String searchText) {
        List<T> result = new ArrayList<>();
        for (T item : items) {
            if (matches(item, searchText)) {
                result.add(item);
            }
        }
        return result;
    }
    
    /**
     * 清除缓存
     */
    public void clearCache() {
        pinyinCache.clear();
    }
}