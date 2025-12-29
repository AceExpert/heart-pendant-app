import { Stack } from 'expo-router';

export default function RootLayout() {

  return (
      <Stack>
        <Stack.Screen name="index" options={{headerShown: false, contentStyle: {backgroundColor: "rgba(255, 255, 255, 1)"}}}/>
      </Stack>
  );
}
