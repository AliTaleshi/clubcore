import { Button, Card, List, ListItem, ListItemText } from '@mui/material';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../../api/client';
import type { Notification } from '../../api/types';
import { Empty, Loading, PageHeader } from '../../components/common';
import { formatDateTime } from '../../utils/format';

export default function NotificationsPage() {
  const qc = useQueryClient();
  const { data, isLoading } = useQuery({
    queryKey: ['notifications'],
    queryFn: () => api.get<Notification[]>('/notifications').then((r) => r.data),
  });
  const readAll = useMutation({
    mutationFn: () => api.post('/notifications/read-all'),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['notifications'] }),
  });
  return (
    <>
      <PageHeader title="اعلان‌ها" actions={<Button onClick={() => readAll.mutate()}>علامت‌گذاری همه به‌عنوان خوانده‌شده</Button>} />
      <Card>
        {isLoading ? <Loading /> : !data?.length ? <Empty text="اعلانی ندارید" /> : (
          <List>
            {data.map((n) => (
              <ListItem key={n.id} divider sx={{ bgcolor: n.read ? undefined : 'action.hover' }}>
                <ListItemText primary={n.title} secondary={`${n.body} — ${formatDateTime(n.createdAt)}`}
                  primaryTypographyProps={{ fontWeight: n.read ? 400 : 700 }} />
              </ListItem>
            ))}
          </List>
        )}
      </Card>
    </>
  );
}
