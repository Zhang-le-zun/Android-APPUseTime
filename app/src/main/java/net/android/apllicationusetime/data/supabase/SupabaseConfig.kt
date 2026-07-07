package net.android.apllicationusetime.data.supabase

/**
 * Supabase 项目配置
 *
 * 项目: Zhang-le-zun's Project
 * 数据库区域: ap-northeast-1 (东京)
 * 数据库表: daily_summaries, app_usage_details
 */
object SupabaseConfig {

    /** 项目引用 ID */
    const val PROJECT_REF = "ylaykdnytmwkscrdxexx"

    /** Supabase 项目 URL */
    const val SUPABASE_URL = "https://ylaykdnytmwkscrdxexx.supabase.co"

    /** REST API 端点前缀 */
    const val REST_URL = "$SUPABASE_URL/rest/v1"

    /** anon 公开密钥（客户端安全，由 Supabase RLS 策略保护） */
    const val ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InlsYXlrZG55dG13a3NjcmR4ZXh4Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODM0MzIwODYsImV4cCI6MjA5OTAwODA4Nn0.sDMlJXqIFbJSLccqV3Tqnvr3B-u2ztCmxb_2skFYcO8"

    /** 数据库表名 */
    const val TABLE_DAILY_SUMMARIES = "daily_summaries"
    const val TABLE_APP_USAGE_DETAILS = "app_usage_details"
}