import { Ionicons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { colors, fonts, radius, space } from '@/lib/theme';

type Props = {
  title?: string;
  description?: string;
};

export default function LoginRequired({
  title = '로그인이 필요해요',
  description = '이 기능은 로그인 후에 이용할 수 있어요.',
}: Props) {
  const router = useRouter();
  return (
    <View style={styles.wrap}>
      <View style={styles.iconCircle}>
        <Ionicons name="lock-closed" size={30} color={colors.primary} />
      </View>
      <Text style={styles.title}>{title}</Text>
      <Text style={styles.desc}>{description}</Text>
      <Pressable style={styles.btn} onPress={() => router.push('/login')}>
        <Text style={styles.btnText}>로그인하기</Text>
        <Ionicons name="chevron-forward" size={15} color={colors.white} />
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: { flex: 1, backgroundColor: colors.bg, alignItems: 'center', justifyContent: 'center', paddingHorizontal: space.xl, gap: 8 },
  iconCircle: { width: 72, height: 72, borderRadius: 36, backgroundColor: colors.primarySoft, alignItems: 'center', justifyContent: 'center', marginBottom: 10 },
  title: { fontSize: 18, fontFamily: fonts.bold, fontWeight: '800', color: colors.text },
  desc: { fontSize: 14, fontFamily: fonts.medium, fontWeight: '500', color: colors.textSub, textAlign: 'center', lineHeight: 20 },
  btn: { flexDirection: 'row', alignItems: 'center', gap: 2, backgroundColor: colors.primary, borderRadius: radius.pill, paddingHorizontal: 24, paddingVertical: 12, marginTop: 18 },
  btnText: { color: colors.white, fontSize: 15, fontFamily: fonts.bold, fontWeight: '800' },
});
