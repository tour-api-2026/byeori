import { Image } from 'expo-image';
import { useRouter } from 'expo-router';
import { ActivityIndicator, FlatList, Pressable, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useTabBarHeight } from '@/components/TabBar';
import { useCoursesQuery } from '@/lib/hooks/queries';
import { colors, fonts, radius, space } from '@/lib/theme';

// 루트 탐색 — 큐레이션 추천 코스(루트) 목록. 탭하면 코스 상세로 이동.
export default function ExploreScreen() {
  const router = useRouter();
  const tabH = useTabBarHeight();
  const { data, isLoading } = useCoursesQuery();

  return (
    <SafeAreaView style={styles.safe} edges={['top']}>
      <View style={styles.header}>
        <Text style={styles.title}>루트 탐색</Text>
      </View>
      {isLoading ? (
        <ActivityIndicator color={colors.primary} style={{ marginTop: 40 }} />
      ) : (
        <FlatList
          data={data ?? []}
          keyExtractor={(c) => String(c.id)}
          contentContainerStyle={[styles.list, { paddingBottom: tabH + space.lg }]}
          ListEmptyComponent={<Text style={styles.empty}>추천 루트가 아직 없어요</Text>}
          renderItem={({ item }) => (
            <Pressable style={styles.card} onPress={() => router.push(`/course/${item.id}`)}>
              {item.coverImageUrl ? (
                <Image source={{ uri: item.coverImageUrl }} style={styles.cover} contentFit="cover" />
              ) : (
                <View style={[styles.cover, styles.coverEmpty]} />
              )}
              <View style={styles.body}>
                {item.theme ? <Text style={styles.theme}>{item.theme}</Text> : null}
                <Text style={styles.cardTitle} numberOfLines={1}>
                  {item.title}
                </Text>
                {item.description ? (
                  <Text style={styles.cardDesc} numberOfLines={2}>
                    {item.description}
                  </Text>
                ) : null}
                {item.durationHours ? <Text style={styles.meta}>약 {item.durationHours}시간</Text> : null}
              </View>
            </Pressable>
          )}
        />
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  header: { paddingHorizontal: space.lg, paddingTop: space.sm, paddingBottom: space.xs },
  title: { fontSize: 22, fontFamily: fonts.bold, color: colors.text },
  list: { padding: space.lg, gap: space.md },
  empty: { textAlign: 'center', marginTop: 40, fontSize: 14, color: colors.textFaint },
  card: {
    backgroundColor: colors.bgCard,
    borderRadius: radius.lg,
    overflow: 'hidden',
    boxShadow: '0px 6px 16px rgba(31,38,76,0.06)',
  },
  cover: { width: '100%', height: 140, backgroundColor: colors.bgSoft },
  coverEmpty: { alignItems: 'center', justifyContent: 'center' },
  body: { padding: space.md, gap: 4 },
  theme: { fontSize: 11, fontFamily: fonts.medium, color: colors.accent },
  cardTitle: { fontSize: 16, fontFamily: fonts.bold, color: colors.text },
  cardDesc: { fontSize: 13, fontFamily: fonts.regular, color: colors.textSub, lineHeight: 18 },
  meta: { marginTop: 2, fontSize: 12, fontFamily: fonts.medium, color: colors.textFaint },
});
