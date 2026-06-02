import { Ionicons } from '@expo/vector-icons';
import { useState } from 'react';
import { ActivityIndicator, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Chip } from '@/components/Chip';
import { VenueRow } from '@/components/VenueRow';
import { useVenuesQuery } from '@/lib/hooks/queries';
import { colors, radius, space } from '@/lib/theme';

const CATS = ['전체', '한복', '카페', '체험', '문화', '맛집'];

export default function SearchScreen() {
  const [keyword, setKeyword] = useState('');
  const [cat, setCat] = useState('전체');
  const { data, isLoading } = useVenuesQuery({
    keyword: keyword || undefined,
    category: cat === '전체' ? undefined : cat,
    size: 50,
  });

  return (
    <SafeAreaView style={styles.safe} edges={['top']}>
      <View style={styles.searchBar}>
        <Ionicons name="search" size={18} color={colors.textFaint} />
        <TextInput
          style={styles.input}
          placeholder="장소 이름으로 검색"
          placeholderTextColor={colors.textFaint}
          value={keyword}
          onChangeText={setKeyword}
        />
      </View>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.chipsScroll} contentContainerStyle={styles.chips}>
        {CATS.map((c) => <Chip key={c} label={c} selected={c === cat} onPress={() => setCat(c)} />)}
      </ScrollView>
      {isLoading ? (
        <ActivityIndicator color={colors.primary} style={{ marginTop: 40 }} />
      ) : (
        <ScrollView contentContainerStyle={styles.list}>
          <Text style={styles.count}>{data?.totalElements ?? 0}개의 장소</Text>
          {data?.content.map((v) => <VenueRow key={v.id} venue={v} />)}
        </ScrollView>
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  searchBar: {
    flexDirection: 'row', alignItems: 'center', gap: 8, margin: space.lg, marginBottom: 8,
    backgroundColor: colors.bgSoft, borderRadius: radius.pill, paddingHorizontal: 16, paddingVertical: 10,
  },
  input: { flex: 1, fontSize: 15, color: colors.text },
  chipsScroll: { flexGrow: 0, flexShrink: 0 },
  chips: { gap: 8, paddingHorizontal: space.lg, paddingBottom: 8, alignItems: 'center' },
  list: { paddingHorizontal: space.lg, paddingBottom: 24 },
  count: { fontSize: 12, color: colors.textFaint, marginVertical: 8 },
});
