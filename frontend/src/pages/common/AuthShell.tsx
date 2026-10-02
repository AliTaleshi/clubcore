import type { ReactNode } from 'react';
import { Avatar, Box, Card, CardContent, Stack, Typography } from '@mui/material';
import FitnessCenterOutlined from '@mui/icons-material/FitnessCenterOutlined';
import { useQuery } from '@tanstack/react-query';
import { api } from '../../api/client';

export function AuthShell({ title, children }: { title: string; children: ReactNode }) {
  const { data: gym } = useQuery({
    queryKey: ['gym-info'],
    queryFn: () => api.get<Record<string, string>>('/public/gym-info').then((r) => r.data),
  });
  return (
    <Box sx={{ minHeight: '100vh', display: 'grid', placeItems: 'center', p: 2,
      background: 'linear-gradient(135deg, rgba(15,118,110,.12), rgba(194,65,12,.08))' }}>
      <Card sx={{ width: '100%', maxWidth: 420 }}>
        <CardContent sx={{ p: { xs: 3, sm: 4 } }}>
          <Stack alignItems="center" spacing={1} sx={{ mb: 3 }}>
            <Avatar sx={{ bgcolor: 'primary.main', width: 56, height: 56 }}><FitnessCenterOutlined /></Avatar>
            <Typography variant="h6">{gym?.name ?? 'کلاب‌کور'}</Typography>
            <Typography color="text.secondary" variant="body2">{title}</Typography>
          </Stack>
          {children}
        </CardContent>
      </Card>
    </Box>
  );
}
