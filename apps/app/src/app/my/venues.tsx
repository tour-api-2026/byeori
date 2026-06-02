import { Ionicons } from '@expo/vector-icons';
import { Stack, useRouter } from 'expo-router';
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { VenueRow } from '@/components/VenueRow';
import { useMyVenuesQuery } from '@/lib/hooks/queries';
import { colors, radius, space } from '@/lib/theme';

export default function MyVenuesScreen() {
  const router = useRouter();
  const { data, isLoading } = useMyVenuesQuery();

  return (
    <View style={styles.safe}>
      <Stack.Screen options={{ title: '내가 등록한 장소' }} />
      <Pressable style={styles.addBtn} onPress={() => router.push('/venue/register')}>
        <Ionicons name="add" size={18} color={colors.white} />
        <Text style={styles.addText}>새 장소 등록</Text>
      </Pressable>
      {isLoading ? (
        <ActivityIndicator color={colors.primary} style={{ marginTop: 40 }} />
      ) : !data?.length ? (
        <View style={styles.empty}><Text style={styles.emptyText}>등록한 장소가 없어요</Text></View>
      ) : (
        <ScrollView contentContainerStyle={{ paddingHorizontal: space.lg }}>
          {data.map((v) => <VenueRow key={v.id} venue={v} />)}
        </ScrollView>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  addBtn: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 6, backgroundColor: colors.primary, borderRadius: radius.md, paddingVertical: 13, margin: space.lg },
  addText: { color: colors.white, fontSize: 14, fontWeight: '800' },
  empty: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  emptyText: { color: colors.textFaint, fontSize: 14 },
});
