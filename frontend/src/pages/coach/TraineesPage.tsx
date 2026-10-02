import { Link as RouterLink } from 'react-router-dom';
import { Button, Card, Table, TableBody, TableCell, TableContainer, TableHead, TableRow } from '@mui/material';
import { useQuery } from '@tanstack/react-query';
import { api } from '../../api/client';
import type { Member } from '../../api/types';
import { Empty, Loading, PageHeader } from '../../components/common';
import { faDigits } from '../../utils/format';

export default function TraineesPage() {
  const { data, isLoading } = useQuery({
    queryKey: ['trainees'],
    queryFn: () => api.get<Member[]>('/coaching/trainees').then((r) => r.data),
  });
  return (
    <>
      <PageHeader title="شاگردان من" subtitle="برای مشاهده سوابق و ثبت برنامه تمرینی روی هر شاگرد کلیک کنید" />
      <Card>
        {isLoading ? <Loading /> : !data?.length ? <Empty text="هنوز شاگردی به شما اختصاص داده نشده است" /> : (
          <TableContainer>
            <Table>
              <TableHead><TableRow><TableCell>نام</TableCell><TableCell>موبایل</TableCell><TableCell>هدف</TableCell><TableCell /></TableRow></TableHead>
              <TableBody>
                {data.map((m) => (
                  <TableRow key={m.id} hover>
                    <TableCell>{m.fullName}</TableCell>
                    <TableCell>{faDigits(m.phone)}</TableCell>
                    <TableCell>{m.goal ?? '—'}</TableCell>
                    <TableCell><Button component={RouterLink} to={`/members/${m.id}`} size="small">پرونده</Button></TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Card>
    </>
  );
}
