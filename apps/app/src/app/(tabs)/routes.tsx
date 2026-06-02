import { Ionicons } from '@expo/vector-icons';
import { Image } from 'expo-image';
import { useRouter } from 'expo-router';
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useCoursesQuery } from '@/lib/hooks/queries';
import { colors, radius, shadow, space } from '@/lib/theme';

export default function RoutesScreen() {
  const router = useRouter();
  const { data, isLoading } = useCoursesQuery();

  return (
    <SafeAreaView style={styles.safe} edges={['top']}>
      <View style={styles.header}>
        <Text style={styles.h1}>추천 루트</Text>
        <Text style={styles.sub}>운영진이 직접 짠 한국 전통 테마 코스</Text>
      </View>
      {isLoading ? (
        <ActivityIndicator color={colors.primary} style={{ marginTop: 40 }} />
      ) : (
        <ScrollView contentContainerStyle={styles.list} showsVerticalScrollIndicator={false}>
          {data?.map((c) => (
            <Pressable key={c.id} style={styles.card} onPress={() => router.push(`/course/${c.id}`)}>
              <Image source={c.coverImageUrl} style={styles.cover} contentFit="cover" transition={200} />
              <View style={styles.body}>
                <View style={styles.themeBadge}><Text style={styles.themeText}>{c.theme}</Text></View>
                <Text style={styles.title}>{c.title}</Text>
                <Text style={styles.desc} numberOfLines={2}>{c.description}</Text>
                <View style={styles.metaRow}>
                  <Ionicons name="time-outline" size={14} color={colors.textFaint} />
                  <Text style={styles.meta}>약 {c.durationHours}시간</Text>
                  <Ionicons name="chevron-forward" size={16} color={colors.textFaint} style={{ marginLeft: 'auto' }} />
                </View>
              </View>
            </Pressable>
          ))}
        </ScrollView>
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  header: { paddingHorizontal: space.lg, paddingTop: 8, paddingBottom: 6 },
  h1: { fontSize: 24, fontWeight: '900', color: colors.text },
  sub: { fontSize: 13, color: colors.textFaint, marginTop: 4 },
  list: { padding: space.lg, gap: 16 },
  card: { borderRadius: radius.lg, backgroundColor: colors.bgCard, overflow: 'hidden', ...shadow.card },
  cover: { width: '100%', height: 150, backgroundColor: colors.bgSoft },
  body: { padding: 14 },
  themeBadge: { alignSelf: 'flex-start', backgroundColor: colors.primarySoft, borderRadius: radius.sm, paddingHorizontal: 8, paddingVertical: 3, marginBottom: 6 },
  themeText: { color: colors.primary, fontSize: 11, fontWeight: '800' },
  title: { fontSize: 17, fontWeight: '800', color: colors.text },
  desc: { fontSize: 13, color: colors.textSub, marginTop: 4 },
  metaRow: { flexDirection: 'row', alignItems: 'center', gap: 4, marginTop: 10 },
  meta: { fontSize: 12, color: colors.textFaint },
});
